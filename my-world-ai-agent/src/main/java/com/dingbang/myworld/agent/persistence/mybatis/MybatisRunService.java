package com.dingbang.myworld.agent.persistence.mybatis;

import com.dingbang.myworld.agent.api.AgentError;
import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.orchestration.PlanEvent;
import com.dingbang.myworld.agent.orchestration.PlanStepStatus;
import com.dingbang.myworld.agent.persistence.RecoveryJournal;
import com.dingbang.myworld.agent.session.ImportedSession;
import com.dingbang.myworld.agent.session.SessionExportCodec;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.agent.tool.ToolExecutionEvent;
import com.dingbang.myworld.agent.tool.ToolExecutionPhase;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.api.ModelTokenUsage;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 通过 MyBatis-Plus Mapper 保存运行日志并在安全边界恢复中断任务。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class MybatisRunService implements RecoveryJournal {
    /** 运行及附属表 Mapper。 */
    private final AgentRunMapper runs;
    /** 会话租约 Mapper。 */
    private final AgentSessionMapper sessions;
    /** 组合写入使用的数据库事务。 */
    private final TransactionTemplate transactions;
    /** 结构化运行 JSON 编解码器。 */
    private final ObjectMapper json = new ObjectMapper();
    /** 部分交换编解码器。 */
    private final SessionExportCodec codec = new SessionExportCodec();

    /**
     * 绑定 Mapper 和短事务。
     *
     * @param runs 运行 Mapper
     * @param sessions 会话 Mapper
     * @param transactions 事务模板
     */
    public MybatisRunService(AgentRunMapper runs, AgentSessionMapper sessions,
                             TransactionTemplate transactions) {
        this.runs = Objects.requireNonNull(runs, "运行 Mapper 不能为空");
        this.sessions = Objects.requireNonNull(sessions, "会话 Mapper 不能为空");
        this.transactions = Objects.requireNonNull(transactions, "事务模板不能为空");
    }

    /**
     * 登记普通运行。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param request 用户请求
     * @param modelId 模型标识
     * @param promptHash 模板哈希
     * @param resumedFromRunId 来源运行
     */
    @Override
    public void start(String runId, String sessionId, AgentRequest request,
                      String modelId, String promptHash, String resumedFromRunId) {
        start(runId, sessionId, request, modelId, promptHash, resumedFromRunId, null, runId);
    }

    /**
     * 保存请求键、可信归属和父子运行关系。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param request 用户请求
     * @param modelId 模型标识
     * @param promptHash 模板哈希
     * @param resumedFromRunId 来源运行
     * @param parentRunId 父运行
     * @param rootRunId 根运行
     */
    @Override
    public void start(String runId, String sessionId, AgentRequest request, String modelId,
                      String promptHash, String resumedFromRunId, String parentRunId, String rootRunId) {
        /** 只保存恢复所需输入的请求 JSON。 */
        ObjectNode input = json.createObjectNode();
        input.put("userText", request.getUserText());
        input.put("hasAttachments", !request.getAttachments().isEmpty());
        /** 运行主表记录。 */
        AgentRunRow row = new AgentRunRow();
        row.setId(runId);
        row.setSessionId(sessionId);
        row.setOwnerKey(request.getOwnerId());
        row.setAppId(request.getAppId());
        row.setAgentId(request.getAgentId());
        row.setRequestId(request.getRequestId());
        row.setParentRunId(parentRunId);
        row.setRootRunId(rootRunId);
        row.setResumedFromRunId(resumedFromRunId);
        row.setStatus("RUNNING");
        row.setModelId(modelId);
        row.setPromptHash(promptHash);
        row.setRequestJson(input.toString());
        row.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        try {
            runs.insert(row);
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("REQUEST_ALREADY_EXISTS", exception);
        }
    }

    /**
     * 原子保存事件与对应计划状态。
     *
     * @param event 运行事件
     */
    @Override
    public void event(AgentEvent event) {
        /** 仅包含结构化运行信息的载荷。 */
        ObjectNode payload = json.createObjectNode();
        if (event.getText() != null) payload.put("text", event.getText());
        if (event.getToolExecution() != null) {
            payload.put("callId", event.getToolExecution().getCallId());
            payload.put("toolName", event.getToolExecution().getToolName());
            payload.put("phase", event.getToolExecution().getPhase().name());
            if (event.getToolExecution().getResult() != null) {
                payload.set("toolResult", json.valueToTree(event.getToolExecution().getResult()));
            }
        }
        if (event.getUsage() != null) payload.set("usage", json.valueToTree(event.getUsage()));
        if (event.getPlan() != null) payload.set("plan", json.valueToTree(event.getPlan()));
        if (event.getResult() != null) payload.set("result", json.valueToTree(event.getResult()));
        transactions.executeWithoutResult(status -> {
            runs.insertEvent(event.getRunId(), event.getSequence(), event.getType().name(), payload.toString(),
                    LocalDateTime.ofInstant(event.getTimestamp(), ZoneOffset.UTC));
            if (event.getPlan() != null) updatePlan(event);
        });
    }

    /**
     * 根据计划事件同步计划与步骤表。
     *
     * @param event 计划事件
     */
    private void updatePlan(AgentEvent event) {
        /** 计划事件载荷。 */
        PlanEvent plan = event.getPlan();
        /** 当前运行的唯一顶层计划标识。 */
        String planId = event.getRunId() + ":plan";
        if (event.getType() == AgentEventType.PLAN_CREATED) {
            runs.insertPlan(planId, event.getRunId(), plan.getPlanName());
            for (int index = 1; index <= plan.getStepCount(); index++) runs.insertPlanStep(planId, index);
        } else if (event.getType() == AgentEventType.PLAN_STEP_STARTED
                || event.getType() == AgentEventType.PLAN_STEP_FINISHED) {
            runs.updatePlanStep(planId, plan.getStepNumber(),
                    event.getType() == AgentEventType.PLAN_STEP_STARTED ? "RUNNING" : plan.getStatus().name(),
                    event.getType() == AgentEventType.PLAN_STEP_FINISHED ? plan.getDetail() : null);
        } else if (event.getType() == AgentEventType.PLAN_FINISHED) {
            runs.finishPlan(planId, plan.getStatus() == null ? "COMPLETED" : plan.getStatus().name());
        }
    }

    /**
     * 在任何外部工具调用前保存参数哈希与执行意图。
     *
     * @param runId 运行标识
     * @param call 工具调用
     */
    @Override
    public void toolStarted(String runId, ToolCall call) {
        runs.insertTool(UUID.randomUUID().toString(), runId, call.getCallId(), call.getName(),
                call.getArgumentsJson(), sha256(call.getArgumentsJson()));
    }

    /**
     * 只将尚未完成的工具意图标记为确定结果。
     *
     * @param runId 运行标识
     * @param result 工具结果
     */
    @Override
    public void toolCompleted(String runId, ToolResult result) {
        if (runs.completeTool(runId, result.getCallId(), result.getStatus().name(),
                json.valueToTree(result).toString()) != 1) {
            throw new IllegalStateException("TOOL_CHECKPOINT_CONFLICT");
        }
    }

    /**
     * 保存唯一运行终态。
     *
     * @param result 最终结果
     */
    @Override
    public void finish(AgentResult result) {
        if (runs.finishRun(result.getRunId(), result.getStatus().name(),
                json.valueToTree(result).toString()) != 1) {
            throw new IllegalStateException("RUN_STATE_CONFLICT");
        }
    }

    /**
     * 保存当前已确定的部分交换。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param request 原请求
     * @param sessionVersion 会话历史版本
     * @param nextModelTurn 下一模型回合
     * @param exchange 已确定消息
     */
    @Override
    public void checkpoint(String runId, String sessionId, AgentRequest request,
                           long sessionVersion, int nextModelTurn, List<Message> exchange) {
        /** 可含尚未配对工具调用的部分交换 JSON。 */
        String checkpointJson = codec.encode(sessionId, request.getOwnerId(), request.getAppId(),
                request.getAgentId(), ModelOptions.empty(), new SessionSnapshot(1, exchange));
        runs.upsertCheckpoint(runId, sessionVersion, nextModelTurn, checkpointJson);
    }

    /**
     * 保存文件产物版本，不保存文件内容。
     *
     * @param runId 运行标识
     * @param operation 文件操作
     * @param path 相对路径
     * @param hash 版本哈希
     */
    @Override
    public void artifact(String runId, String operation, String path, String hash) {
        /** 是否为删除操作。 */
        boolean deleted = "DELETE".equalsIgnoreCase(operation);
        runs.insertArtifact(UUID.randomUUID().toString(), runId, path, operation,
                deleted ? hash : null, deleted ? null : hash);
    }

    /**
     * 按可信归属查询产物。
     *
     * @param runId 运行标识
     * @param ownerId 所有者
     * @param appId 应用
     * @return 已保存产物
     */
    @Override
    public List<ArtifactRecord> artifacts(String runId, String ownerId, String appId) {
        if (find(runId, ownerId, appId) == null) throw new IllegalArgumentException("未知的运行标识或归属不匹配");
        /** 按写入顺序排列的产物。 */
        List<ArtifactRecord> artifacts = new ArrayList<>();
        for (Map<String, Object> row : runs.artifactRows(runId)) {
            artifacts.add(new ArtifactRecord(runId, value(row, "operation"), value(row, "path"),
                    value(row, "beforeHash"), value(row, "afterHash")));
        }
        return List.copyOf(artifacts);
    }

    /**
     * 读取安全检查点并补齐已落库的确定工具结果。
     *
     * @param runId 原运行标识
     * @return 可恢复消息边界，不存在时为空
     */
    @Override
    public RecoveryCheckpoint recoveryCheckpoint(String runId) {
        /** 最新检查点行。 */
        Map<String, Object> row = runs.checkpointRow(runId);
        if (row == null) return null;
        /** 会话历史版本。 */
        long version = ((Number) row.get("sessionVersion")).longValue();
        /** 下一个模型回合序号。 */
        int nextTurn = ((Number) row.get("nextModelTurn")).intValue();
        /** 已验证的部分交换。 */
        ImportedSession decoded = codec.decodeCheckpoint(value(row, "exchangeJson"));
        /** 可补充工具结果的消息副本。 */
        List<Message> messages = new ArrayList<>(decoded.getMessages());
        /** 最后一批助手工具调用。 */
        List<ToolCall> calls = List.of();
        /** 检查点中已包含的工具结果标识。 */
        Set<String> knownResults = new HashSet<>();
        for (Message message : messages) {
            if (!message.getToolCalls().isEmpty()) {
                calls = message.getToolCalls();
                knownResults.clear();
            }
            for (ToolResult result : message.getToolResults()) knownResults.add(result.getCallId());
        }
        /** 数据库中已确认的工具结果。 */
        Map<String, ToolResult> persisted = toolResults(runId);
        /** 可在完整步骤边界恢复的计划。 */
        PlanRecovery plan = planRecovery(runId);
        if (!calls.isEmpty() && knownResults.isEmpty() && persisted.isEmpty() && !hasToolExecutions(runId)) {
            messages.remove(messages.size() - 1);
            return new RecoveryCheckpoint(version, nextTurn, List.copyOf(messages));
        }
        for (ToolCall call : calls) {
            if (knownResults.contains(call.getCallId())) continue;
            if (plan != null && "create_plan".equals(call.getName())
                    && plan.call().getCallId().equals(call.getCallId())) continue;
            /** 已确认的原调用结果。 */
            ToolResult result = persisted.get(call.getCallId());
            if (result == null) throw new IllegalStateException("NEEDS_REVIEW");
            messages.add(new Message(runId + ":recovered-tool:" + call.getCallId(), Role.TOOL,
                    List.of(), List.of(), List.of(result), Map.of()));
        }
        return new RecoveryCheckpoint(version, nextTurn, List.copyOf(messages));
    }

    /**
     * 按调用标识索引已确认的工具结果。
     *
     * @param runId 运行标识
     * @return 已确认工具结果
     */
    private Map<String, ToolResult> toolResults(String runId) {
        /** 工具结果索引。 */
        Map<String, ToolResult> results = new HashMap<>();
        for (Map<String, Object> row : runs.toolResultRows(runId)) {
            try {
                /** 结构化工具结果。 */
                JsonNode node = json.readTree(value(row, "resultJson"));
                /** 原调用标识。 */
                String callId = value(row, "callId");
                results.put(callId, new ToolResult(callId,
                        ToolResultStatus.valueOf(node.path("status").asText()),
                        node.path("content").asText(), textOrNull(node, "errorCode"),
                        node.path("truncated").asBoolean()));
            } catch (Exception exception) {
                throw new IllegalStateException("INVALID_TOOL_CHECKPOINT", exception);
            }
        }
        return results;
    }

    /**
     * 读取停在完整步骤边界的计划。
     *
     * @param runId 原运行标识
     * @return 可恢复计划；边界不安全时为空
     */
    @Override
    public PlanRecovery planRecovery(String runId) {
        /** 尚未完成的计划工具调用。 */
        List<Map<String, Object>> calls = runs.planCallRows(runId);
        if (calls.size() != 1) return null;
        /** 原计划调用。 */
        ToolCall call = new ToolCall(value(calls.get(0), "callId"), "create_plan",
                value(calls.get(0), "argsJson"));
        /** 按顺序保存的步骤状态。 */
        List<PlanStepStatus> statuses = new ArrayList<>();
        /** 按顺序保存的步骤报告。 */
        List<String> results = new ArrayList<>();
        for (Map<String, Object> row : runs.planStepRows(runId)) {
            /** 当前步骤状态。 */
            PlanStepStatus state = PlanStepStatus.valueOf(value(row, "status"));
            if (state == PlanStepStatus.RUNNING) return null;
            statuses.add(state);
            results.add(value(row, "result"));
        }
        if (statuses.isEmpty()) return null;
        return new PlanRecovery(call, List.copyOf(statuses),
                Collections.unmodifiableList(new ArrayList<>(results)));
    }

    /**
     * 扫描租约失效的运行。
     *
     * @return 已中断运行标识
     */
    @Override
    public List<String> interruptAbandoned() {
        return interruptRuns(false);
    }

    /**
     * 单实例启动时立即扫描并释放旧运行租约。
     *
     * @return 已中断运行标识
     */
    @Override
    public List<String> interruptOnStartup() {
        return interruptRuns(true);
    }

    /**
     * 在短事务内转换运行状态和租约。
     *
     * @param startup 是否为单实例重启扫描
     * @return 实际更新的运行标识
     */
    private List<String> interruptRuns(boolean startup) {
        /** 本次扫描到的运行。 */
        List<String> candidates = startup ? runs.runningRunIds()
                : runs.abandonedRunIds(LocalDateTime.now(ZoneOffset.UTC));
        /** 实际完成状态转换的运行。 */
        List<String> affected = new ArrayList<>();
        for (String runId : candidates) {
            transactions.executeWithoutResult(status -> {
                /** 安全步骤边界允许忽略尚未完成的计划工具意图。 */
                boolean resumablePlan = planRecovery(runId) != null;
                /** 存在未知结果的非计划工具。 */
                boolean uncertain = (resumablePlan ? runs.startedNonPlanToolCount(runId)
                        : runs.startedToolCount(runId)) > 0;
                if (runs.interruptRun(runId, uncertain ? "NEEDS_REVIEW" : "INTERRUPTED") == 1) {
                    if (startup) sessions.releaseRunLease(runId);
                    affected.add(runId);
                }
            });
        }
        return List.copyOf(affected);
    }

    /**
     * 按可信归属查询运行信息。
     *
     * @param runId 运行标识
     * @param ownerId 所有者
     * @param appId 应用
     * @return 运行信息；不存在或归属不符时为空
     */
    @Override
    public RunRecord find(String runId, String ownerId, String appId) {
        /** 运行主表记录。 */
        AgentRunRow row = runs.selectById(runId);
        if (row == null || !row.getOwnerKey().equals(ownerId) || !row.getAppId().equals(appId)) return null;
        try {
            /** 原始请求的最小 JSON。 */
            JsonNode input = json.readTree(row.getRequestJson());
            return new RunRecord(runId, row.getSessionId(), ownerId, appId, row.getAgentId(),
                    row.getRequestId(), AgentResultStatus.valueOf(row.getStatus()),
                    input.path("userText").asText(), input.path("hasAttachments").asBoolean(),
                    row.getResumedFromRunId());
        } catch (Exception exception) {
            throw new IllegalStateException("PERSISTENCE_ERROR", exception);
        }
    }

    /**
     * 按可信归属读取终态结果。
     *
     * @param runId 运行标识
     * @param ownerId 所有者
     * @param appId 应用
     * @return 终态结果；尚未结束时为空
     */
    @Override
    public AgentResult result(String runId, String ownerId, String appId) {
        if (find(runId, ownerId, appId) == null) return null;
        /** 运行主表记录。 */
        AgentRunRow row = runs.selectById(runId);
        if (row.getResultJson() == null) return null;
        try {
            return decodeResult(json.readTree(row.getResultJson()));
        } catch (Exception exception) {
            throw new IllegalStateException("PERSISTENCE_ERROR", exception);
        }
    }

    /**
     * 按归属与运行内序号回放事件。
     *
     * @param runId 运行标识
     * @param ownerId 所有者
     * @param appId 应用
     * @param afterSequence 已消费序号
     * @return 最多一千条后续事件
     */
    @Override
    public List<AgentEvent> events(String runId, String ownerId, String appId, long afterSequence) {
        if (afterSequence < 0) throw new IllegalArgumentException("事件序号不能为负数");
        if (find(runId, ownerId, appId) == null) throw new IllegalArgumentException("未知的运行标识或归属不匹配");
        /** 有序事件页。 */
        List<AgentEvent> events = new ArrayList<>();
        for (Map<String, Object> row : runs.eventRows(runId, afterSequence)) {
            try {
                /** 当前事件载荷。 */
                JsonNode payload = json.readTree(value(row, "payloadJson"));
                /** 事件类型。 */
                AgentEventType type = AgentEventType.valueOf(value(row, "type"));
                /** 可选工具阶段。 */
                ToolExecutionEvent tool = type == AgentEventType.TOOL_EXECUTION
                        ? new ToolExecutionEvent(payload.path("callId").asText(),
                        payload.path("toolName").asText(),
                        ToolExecutionPhase.valueOf(payload.path("phase").asText()),
                        payload.has("toolResult") ? decodeToolResult(payload.path("toolResult")) : null)
                        : null;
                /** 可选用量。 */
                ModelTokenUsage usage = payload.has("usage") ? decodeUsage(payload.path("usage")) : null;
                /** 可选计划状态。 */
                PlanEvent plan = null;
                if (payload.has("plan")) {
                    /** 计划载荷。 */
                    JsonNode node = payload.path("plan");
                    plan = new PlanEvent(node.path("planName").asText(), node.path("stepNumber").asInt(),
                            node.path("stepCount").asInt(), node.path("status").isNull() ? null
                            : PlanStepStatus.valueOf(node.path("status").asText()), node.path("detail").asText());
                }
                events.add(new AgentEvent(runId, value(row, "sessionId"),
                        ((Number) row.get("seq")).longValue(), eventInstant(row.get("createdAt")), type,
                        payload.has("text") ? payload.path("text").asText() : null,
                        payload.has("result") ? decodeResult(payload.path("result")) : null,
                        tool, usage, plan));
            } catch (Exception exception) {
                throw new IllegalStateException("PERSISTENCE_ERROR", exception);
            }
        }
        return List.copyOf(events);
    }

    /**
     * 将数据库时间转换为 UTC 时刻。
     *
     * @param value Mapper 读取的时间值
     * @return 事件时刻
     */
    private static Instant eventInstant(Object value) {
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("INVALID_EVENT_TIMESTAMP");
    }

    /**
     * 从受控 JSON 重建最终结果。
     *
     * @param node 结果节点
     * @return 运行结果
     */
    private static AgentResult decodeResult(JsonNode node) {
        /** 可选错误节点。 */
        JsonNode error = node.path("error");
        return new AgentResult(node.path("runId").asText(), node.path("sessionId").asText(),
                node.path("requestId").asText(), AgentResultStatus.valueOf(node.path("status").asText()),
                textOrNull(node, "finalText"), node.path("finishReason").isNull() ? null
                : ModelFinishReason.valueOf(node.path("finishReason").asText()),
                error.isObject() ? new AgentError(error.path("code").asText(), error.path("message").asText()) : null,
                node.path("promptTemplateId").asText(), node.path("promptHash").asText(),
                node.path("usage").isObject() ? decodeUsage(node.path("usage")) : null);
    }

    /**
     * 从受控 JSON 重建工具结果。
     *
     * @param node 工具结果节点
     * @return 工具结果
     */
    private static ToolResult decodeToolResult(JsonNode node) {
        return new ToolResult(node.path("callId").asText(),
                ToolResultStatus.valueOf(node.path("status").asText()),
                node.path("content").asText(), textOrNull(node, "errorCode"),
                node.path("truncated").asBoolean());
    }

    /**
     * 从受控 JSON 重建模型用量。
     *
     * @param node 用量节点
     * @return 模型用量
     */
    private static ModelTokenUsage decodeUsage(JsonNode node) {
        return new ModelTokenUsage(node.path("promptTokens").asLong(),
                node.path("completionTokens").asLong(), node.path("totalTokens").asLong(),
                node.path("reasoningTokens").isNumber() ? node.path("reasoningTokens").asLong() : null);
    }

    /**
     * 读取可空文本字段。
     *
     * @param node 父节点
     * @param field 字段名
     * @return 文本或空值
     */
    private static String textOrNull(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
    }

    /**
     * 从数据库映射读取可空文本。
     *
     * @param row 数据库行
     * @param key 列别名
     * @return 文本或空值
     */
    private static String value(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : row.get(key).toString();
    }

    /**
     * 判断运行是否存在工具意图。
     *
     * @param runId 运行标识
     * @return 存在时为真
     */
    @Override
    public boolean hasToolExecutions(String runId) {
        return runs.toolCount(runId) > 0;
    }

    /**
     * 对工具参数计算稳定 SHA-256 哈希。
     *
     * @param text 参数 JSON
     * @return 小写十六进制哈希
     */
    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
