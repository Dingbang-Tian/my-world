package com.dingbang.myworld.agent.persistence.jdbc;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentError;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.orchestration.PlanEvent;
import com.dingbang.myworld.agent.orchestration.PlanStepStatus;
import com.dingbang.myworld.agent.tool.ToolExecutionEvent;
import com.dingbang.myworld.agent.tool.ToolExecutionPhase;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelTokenUsage;
import com.dingbang.myworld.agent.persistence.RecoveryJournal;
import com.dingbang.myworld.agent.session.ImportedSession;
import com.dingbang.myworld.agent.session.SessionExportCodec;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;

/**
 * 将运行、事件、工具意图和计划状态写入短事务的 JDBC 日志。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class JdbcRunJournal implements RecoveryJournal {
    /** 已迁移数据库。 */
    private final AgentJdbcDatabase database;
    /** JSON 编解码器。 */
    private final ObjectMapper mapper = new ObjectMapper();
    /** 保留模型消息顺序的检查点编解码器。 */
    private final SessionExportCodec codec = new SessionExportCodec();

    /**
     * 绑定运行数据库。
     *
     * @param database 数据库连接提供者
     */
    public JdbcRunJournal(AgentJdbcDatabase database) {
        this.database = Objects.requireNonNull(database, "数据库不能为 null");
    }

    /**
     * 登记运行并使用数据库唯一约束保护请求幂等键。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param request 请求
     * @param modelId 可信模型标识
     * @param promptHash 模板哈希
     * @param resumedFromRunId 来源运行，可为空
     */
    @Override
    public void start(String runId, String sessionId, AgentRequest request,
                      String modelId, String promptHash, String resumedFromRunId) {
        start(runId, sessionId, request, modelId, promptHash, resumedFromRunId, null, runId);
    }

    /**
     * 保存父子运行关系并登记唯一请求。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param request 用户请求
     * @param modelId 模型标识
     * @param promptHash 模板哈希
     * @param resumedFromRunId 恢复来源
     * @param parentRunId 父运行
     * @param rootRunId 根运行
     */
    @Override
    public void start(String runId, String sessionId, AgentRequest request, String modelId,
                      String promptHash, String resumedFromRunId, String parentRunId, String rootRunId) {
        /** 仅保存恢复所需输入，不保存模型客户端、凭据和附件字节。 */
        ObjectNode input = mapper.createObjectNode();
        input.put("userText", request.getUserText());
        input.put("hasAttachments", !request.getAttachments().isEmpty());
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO agent_run(id,session_id,owner_key,app_id,agent_id,request_id,parent_run_id,root_run_id,resumed_from_run_id,status,model_id,prompt_hash,request_json,started_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)")) {
            statement.setString(1, runId);
            statement.setString(2, sessionId);
            statement.setString(3, request.getOwnerId());
            statement.setString(4, request.getAppId());
            statement.setString(5, request.getAgentId());
            statement.setString(6, request.getRequestId());
            statement.setString(7, parentRunId);
            statement.setString(8, rootRunId);
            statement.setString(9, resumedFromRunId);
            statement.setString(10, "RUNNING");
            statement.setString(11, modelId);
            statement.setString(12, promptHash);
            statement.setString(13, input.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new IllegalStateException("REQUEST_ALREADY_EXISTS", exception);
            }
            throw persistence(exception);
        }
    }

    /**
     * 保存事件，并同步计划步骤状态以支持启动后的审查。
     *
     * @param event 运行事件
     */
    @Override
    public void event(AgentEvent event) {
        /** 有界结构化事件载荷。 */
        ObjectNode payload = mapper.createObjectNode();
        if (event.getText() != null) payload.put("text", event.getText());
        if (event.getToolExecution() != null) {
            payload.put("callId", event.getToolExecution().getCallId());
            payload.put("toolName", event.getToolExecution().getToolName());
            payload.put("phase", event.getToolExecution().getPhase().name());
            if (event.getToolExecution().getResult() != null) {
                payload.set("toolResult", mapper.valueToTree(event.getToolExecution().getResult()));
            }
        }
        if (event.getUsage() != null) payload.set("usage", mapper.valueToTree(event.getUsage()));
        if (event.getPlan() != null) payload.set("plan", mapper.valueToTree(event.getPlan()));
        if (event.getResult() != null) payload.set("result", mapper.valueToTree(event.getResult()));
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO agent_run_event(run_id,seq,type,payload_json,created_at) VALUES(?,?,?,?,?)")) {
                    statement.setString(1, event.getRunId());
                    statement.setLong(2, event.getSequence());
                    statement.setString(3, event.getType().name());
                    statement.setString(4, payload.toString());
                    statement.setObject(5, event.getTimestamp());
                    statement.executeUpdate();
                }
                if (event.getPlan() != null) updatePlan(connection, event);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 更新计划与当前步骤的持久状态。
     *
     * @param connection 当前事务连接
     * @param event 计划事件
     * @throws SQLException 数据库故障时
     */
    private void updatePlan(Connection connection, AgentEvent event) throws SQLException {
        /** 计划载荷。 */
        PlanEvent plan = event.getPlan();
        /** 每个运行只允许一个顶层计划的标识。 */
        String planId = event.getRunId() + ":plan";
        if (event.getType() == AgentEventType.PLAN_CREATED) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO agent_plan(id,run_id,name,status,created_at) VALUES(?,?,?,'RUNNING',CURRENT_TIMESTAMP)")) {
                statement.setString(1, planId);
                statement.setString(2, event.getRunId());
                statement.setString(3, plan.getPlanName());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO agent_plan_step(plan_id,step_index,status,updated_at) VALUES(?,?,'PENDING',CURRENT_TIMESTAMP)")) {
                for (int index = 1; index <= plan.getStepCount(); index++) {
                    statement.setString(1, planId);
                    statement.setInt(2, index);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        } else if (event.getType() == AgentEventType.PLAN_STEP_STARTED
                || event.getType() == AgentEventType.PLAN_STEP_FINISHED) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE agent_plan_step SET status=?,result=?,updated_at=CURRENT_TIMESTAMP WHERE plan_id=? AND step_index=?")) {
                statement.setString(1, event.getType() == AgentEventType.PLAN_STEP_STARTED
                        ? "RUNNING" : plan.getStatus().name());
                statement.setString(2, event.getType() == AgentEventType.PLAN_STEP_FINISHED ? plan.getDetail() : null);
                statement.setString(3, planId);
                statement.setInt(4, plan.getStepNumber());
                statement.executeUpdate();
            }
        } else if (event.getType() == AgentEventType.PLAN_FINISHED) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE agent_plan SET status=?,ended_at=CURRENT_TIMESTAMP WHERE id=?")) {
                statement.setString(1, plan.getStatus() == null ? "COMPLETED" : plan.getStatus().name());
                statement.setString(2, planId);
                statement.executeUpdate();
            }
        }
    }

    /**
     * 在调用任何外部工具前保存含参数哈希的意图。
     *
     * @param runId 运行标识
     * @param call 工具调用
     */
    @Override
    public void toolStarted(String runId, ToolCall call) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO agent_tool_execution(id,run_id,call_id,name,args_json,args_hash,status,started_at) VALUES(?,?,?,?,?,?,'STARTED',CURRENT_TIMESTAMP)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, runId);
            statement.setString(3, call.getCallId());
            statement.setString(4, call.getName());
            statement.setString(5, call.getArgumentsJson());
            statement.setString(6, sha256(call.getArgumentsJson()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 标记已确定的工具结果；零行更新表示意图丢失。
     *
     * @param runId 运行标识
     * @param result 工具结果
     */
    @Override
    public void toolCompleted(String runId, ToolResult result) {
        /** 已确定的工具结果 JSON。 */
        String json = mapper.valueToTree(result).toString();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE agent_tool_execution SET status=?,result_json=?,ended_at=CURRENT_TIMESTAMP WHERE run_id=? AND call_id=? AND status='STARTED'")) {
            statement.setString(1, result.getStatus().name());
            statement.setString(2, json);
            statement.setString(3, runId);
            statement.setString(4, result.getCallId());
            if (statement.executeUpdate() != 1) throw new IllegalStateException("TOOL_CHECKPOINT_CONFLICT");
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 将唯一运行终态和结果原子写入记录。
     *
     * @param result 运行结果
     */
    @Override
    public void finish(AgentResult result) {
        /** 不包含凭据的结果 JSON。 */
        String json = mapper.valueToTree(result).toString();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE agent_run SET status=?,result_json=?,ended_at=CURRENT_TIMESTAMP WHERE id=? AND status='RUNNING'")) {
            statement.setString(1, result.getStatus().name());
            statement.setString(2, json);
            statement.setString(3, result.getRunId());
            if (statement.executeUpdate() != 1) throw new IllegalStateException("RUN_STATE_CONFLICT");
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 保存本轮已知消息，覆盖同一运行的上一安全边界。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param request 原用户请求
     * @param sessionVersion 历史版本
     * @param nextModelTurn 下一模型回合
     * @param exchange 已确定的本轮消息
     */
    @Override
    public void checkpoint(String runId, String sessionId, AgentRequest request,
                           long sessionVersion, int nextModelTurn, List<Message> exchange) {
        /** 允许尚未结束的用户或工具交换。 */
        String json = codec.encode(sessionId, request.getOwnerId(), request.getAppId(), request.getAgentId(),
                ModelOptions.empty(), new SessionSnapshot(1, exchange));
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "MERGE INTO agent_run_checkpoint(run_id,session_version,next_model_turn,exchange_json,updated_at) KEY(run_id) VALUES(?,?,?,?,CURRENT_TIMESTAMP)")) {
            statement.setString(1, runId);
            statement.setLong(2, sessionVersion);
            statement.setInt(3, nextModelTurn);
            statement.setString(4, json);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 保存文件工具确认过的路径和版本，不在数据库内保存整个文件。
     *
     * @param runId 运行标识
     * @param operation 文件操作
     * @param path 相对路径
     * @param hash 版本哈希
     */
    @Override
    public void artifact(String runId, String operation, String path, String hash) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO codegen_artifact(id,run_id,path,operation,before_hash,after_hash,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, runId);
            statement.setString(3, path);
            statement.setString(4, operation);
            statement.setString(5, "DELETE".equalsIgnoreCase(operation) ? hash : null);
            statement.setString(6, "DELETE".equalsIgnoreCase(operation) ? null : hash);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 按可信归属读取代码生成产物元数据。
     *
     * @param runId 运行标识
     * @param ownerId 所有者
     * @param appId 应用标识
     * @return 已提交产物记录
     */
    public List<ArtifactRecord> artifacts(String runId, String ownerId, String appId) {
        if (find(runId, ownerId, appId) == null) throw new IllegalArgumentException("未知的运行标识或归属不匹配");
        /** 按发生顺序收集的产物。 */
        List<ArtifactRecord> artifacts = new ArrayList<>();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT path,operation,before_hash,after_hash FROM codegen_artifact WHERE run_id=? ORDER BY created_at,id")) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) artifacts.add(new ArtifactRecord(runId, rows.getString(2),
                        rows.getString(1), rows.getString(3), rows.getString(4)));
            }
            return List.copyOf(artifacts);
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 读取安全检查点，并用已落库的工具结果补齐崩溃窗口中的消息。
     *
     * @param runId 原运行标识
     * @return 可继续的检查点；尚未写入时为 null
     */
    public RecoveryCheckpoint recoveryCheckpoint(String runId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT session_version,next_model_turn,exchange_json FROM agent_run_checkpoint WHERE run_id=?")) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                /** 检查点历史版本。 */
                long version = rows.getLong(1);
                /** 模型续接回合号。 */
                int nextTurn = rows.getInt(2);
                /** 已记录的部分交换。 */
                ImportedSession decoded = codec.decodeCheckpoint(rows.getString(3));
                /** 可补充工具结果的消息副本。 */
                List<Message> messages = new ArrayList<>(decoded.getMessages());
                /** 最后一批助手工具调用。 */
                List<ToolCall> calls = List.of();
                /** 检查点中已有的工具结果标识。 */
                Set<String> knownResults = new HashSet<>();
                for (Message message : messages) {
                    if (!message.getToolCalls().isEmpty()) {
                        calls = message.getToolCalls();
                        knownResults.clear();
                    }
                    for (ToolResult result : message.getToolResults()) knownResults.add(result.getCallId());
                }
                /** 已落库的确定工具结果。 */
                Map<String, ToolResult> persisted = toolResults(connection, runId);
                /** 可在完成步骤边界继续的计划。 */
                PlanRecovery plan = planRecovery(runId);
                if (!calls.isEmpty() && knownResults.isEmpty() && persisted.isEmpty()
                        && !hasToolExecutions(runId)) {
                    /** 调用意图尚未落库，故可安全丢弃未执行的助手工具请求。 */
                    messages.remove(messages.size() - 1);
                    return new RecoveryCheckpoint(version, nextTurn, List.copyOf(messages));
                }
                for (ToolCall call : calls) {
                    if (knownResults.contains(call.getCallId())) continue;
                    if (plan != null && "create_plan".equals(call.getName())
                            && plan.call().getCallId().equals(call.getCallId())) continue;
                    /** 仅复用数据库中明确完成的原调用结果。 */
                    ToolResult result = persisted.get(call.getCallId());
                    if (result == null) throw new IllegalStateException("NEEDS_REVIEW");
                    messages.add(new Message(runId + ":recovered-tool:" + call.getCallId(), Role.TOOL,
                            List.of(), List.of(), List.of(result), Map.of()));
                }
                return new RecoveryCheckpoint(version, nextTurn, List.copyOf(messages));
            }
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 加载一轮中数据库已确认的工具结果。
     *
     * @param connection 当前连接
     * @param runId 运行标识
     * @return 按调用标识索引的结果
     * @throws SQLException 查询失败时
     */
    private Map<String, ToolResult> toolResults(Connection connection, String runId) throws SQLException {
        /** 已确定的工具结果。 */
        Map<String, ToolResult> results = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT call_id,result_json FROM agent_tool_execution WHERE run_id=? AND status<>'STARTED'")) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    try {
                        /** 结构化结果 JSON。 */
                        JsonNode node = mapper.readTree(rows.getString(2));
                        results.put(rows.getString(1), new ToolResult(rows.getString(1),
                                ToolResultStatus.valueOf(node.path("status").asText()),
                                node.path("content").asText(), node.path("errorCode").isNull()
                                ? null : node.path("errorCode").asText(null), node.path("truncated").asBoolean()));
                    } catch (Exception exception) {
                        throw new IllegalStateException("INVALID_TOOL_CHECKPOINT", exception);
                    }
                }
            }
        }
        return results;
    }

    /**
     * 读取停在已完成步骤边界的计划状态。
     *
     * @param runId 原运行标识
     * @return 可恢复计划；不存在安全边界时为 null
     */
    public PlanRecovery planRecovery(String runId) {
        try (Connection connection = database.open();
             PreparedStatement callQuery = connection.prepareStatement(
                     "SELECT call_id,args_json FROM agent_tool_execution WHERE run_id=? AND name='create_plan' AND status='STARTED'")) {
            callQuery.setString(1, runId);
            try (ResultSet calls = callQuery.executeQuery()) {
                if (!calls.next()) return null;
                /** 原计划调用。 */
                ToolCall call = new ToolCall(calls.getString(1), "create_plan", calls.getString(2));
                if (calls.next()) return null;
                /** 按序保存的步骤状态。 */
                List<PlanStepStatus> statuses = new ArrayList<>();
                /** 按序保存的步骤结果。 */
                List<String> results = new ArrayList<>();
                try (PreparedStatement steps = connection.prepareStatement(
                        "SELECT s.status,s.result FROM agent_plan_step s JOIN agent_plan p ON p.id=s.plan_id WHERE p.run_id=? AND p.status='RUNNING' ORDER BY s.step_index")) {
                    steps.setString(1, runId);
                    try (ResultSet rows = steps.executeQuery()) {
                        while (rows.next()) {
                            /** 当前步骤状态。 */
                            PlanStepStatus status = PlanStepStatus.valueOf(rows.getString(1));
                            if (status == PlanStepStatus.RUNNING) return null;
                            statuses.add(status);
                            results.add(rows.getString(2));
                        }
                    }
                }
                if (statuses.isEmpty()) return null;
                return new PlanRecovery(call, List.copyOf(statuses),
                        java.util.Collections.unmodifiableList(new ArrayList<>(results)));
            }
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 启动时只扫描租约失效的运行，未知工具结果进入人工核查状态。
     *
     * @return 被标记的运行标识
     */
    public List<String> interruptAbandoned() {
        return interruptRuns(false);
    }

    /**
     * 单实例重启时立即标记此前未结束的运行并释放旧进程租约。
     *
     * @return 转换状态的运行标识
     */
    public List<String> interruptOnStartup() {
        return interruptRuns(true);
    }

    /**
     * 按租约过期或单实例启动规则转换未结束运行。
     *
     * @param startup 是否作为单实例重启扫描
     * @return 转换状态的运行标识
     */
    private List<String> interruptRuns(boolean startup) {
        /** 本次被转换的运行。 */
        List<String> affected = new ArrayList<>();
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement query = connection.prepareStatement(startup
                    ? "SELECT id FROM agent_run WHERE status='RUNNING'"
                    : "SELECT r.id FROM agent_run r JOIN agent_session s ON s.id=r.session_id WHERE r.status='RUNNING' AND (s.lease_until IS NULL OR s.lease_until<CURRENT_TIMESTAMP)")) {
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) affected.add(rows.getString(1));
                }
                for (String runId : affected) {
                    /** 存在结果未知的工具意图。 */
                    boolean uncertain;
                    /** 只有计划工具未完成且步骤处于安全边界时可继续。 */
                    boolean resumablePlan = planRecovery(runId) != null;
                    try (PreparedStatement tools = connection.prepareStatement(resumablePlan
                            ? "SELECT COUNT(*) FROM agent_tool_execution WHERE run_id=? AND status='STARTED' AND name<>'create_plan'"
                            : "SELECT COUNT(*) FROM agent_tool_execution WHERE run_id=? AND status='STARTED'")) {
                        tools.setString(1, runId);
                        try (ResultSet rows = tools.executeQuery()) {
                            rows.next();
                            uncertain = rows.getLong(1) > 0;
                        }
                    }
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE agent_run SET status=?,ended_at=CURRENT_TIMESTAMP WHERE id=? AND status='RUNNING'")) {
                        update.setString(1, uncertain ? "NEEDS_REVIEW" : "INTERRUPTED");
                        update.setString(2, runId);
                        update.executeUpdate();
                    }
                    if (startup) {
                        try (PreparedStatement release = connection.prepareStatement(
                                "UPDATE agent_session SET lease_owner=NULL,lease_until=NULL WHERE id=(SELECT session_id FROM agent_run WHERE id=?)")) {
                            release.setString(1, runId);
                            release.executeUpdate();
                        }
                    }
                }
                connection.commit();
                return affected;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 按可信归属读取用于显式恢复的运行记录。
     *
     * @param runId 原运行标识
     * @param ownerId 所有者
     * @param appId 应用
     * @return 运行记录，不存在或不属于调用方时为 null
     */
    public RunRecord find(String runId, String ownerId, String appId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT session_id,agent_id,request_id,status,request_json,resumed_from_run_id FROM agent_run WHERE id=? AND owner_key=? AND app_id=?")) {
            statement.setString(1, runId);
            statement.setString(2, ownerId);
            statement.setString(3, appId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                /** 原始输入 JSON。 */
                JsonNode input = mapper.readTree(rows.getString(5));
                return new RunRecord(runId, rows.getString(1), ownerId, appId, rows.getString(2),
                        rows.getString(3), AgentResultStatus.valueOf(rows.getString(4)),
                        input.path("userText").asText(), input.path("hasAttachments").asBoolean(),
                        rows.getString(6));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("PERSISTENCE_ERROR", exception);
        }
    }

    /**
     * 按归属查询已提交的运行终态结果。
     *
     * @param runId 运行标识
     * @param ownerId 可信所有者
     * @param appId 应用标识
     * @return 已保存结果；运行未结束时为 null
     */
    public AgentResult result(String runId, String ownerId, String appId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT result_json FROM agent_run WHERE id=? AND owner_key=? AND app_id=?")) {
            statement.setString(1, runId);
            statement.setString(2, ownerId);
            statement.setString(3, appId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getString(1) == null) return null;
                return decodeResult(mapper.readTree(rows.getString(1)));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("PERSISTENCE_ERROR", exception);
        }
    }

    /**
     * 从文件库按顺序查询运行事件，拒绝跨所有者访问。
     *
     * @param runId 运行标识
     * @param ownerId 可信所有者
     * @param appId 应用标识
     * @param afterSequence 已消费序号
     * @return 最多一千条后续事件，调用方可用末尾序号继续分页
     */
    public List<AgentEvent> events(String runId, String ownerId, String appId, long afterSequence) {
        if (afterSequence < 0) throw new IllegalArgumentException("事件序号不能为负数");
        if (find(runId, ownerId, appId) == null) throw new IllegalArgumentException("未知的运行标识或归属不匹配");
        /** 按运行内序号读取的事件。 */
        List<AgentEvent> events = new ArrayList<>();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT r.session_id,e.seq,e.type,e.payload_json,e.created_at FROM agent_run_event e JOIN agent_run r ON r.id=e.run_id WHERE e.run_id=? AND e.seq>? ORDER BY e.seq LIMIT 1000")) {
            statement.setString(1, runId);
            statement.setLong(2, afterSequence);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    /** 当前结构化事件载荷。 */
                    JsonNode payload = mapper.readTree(rows.getString(4));
                    /** 事件类型。 */
                    AgentEventType type = AgentEventType.valueOf(rows.getString(3));
                    /** 工具阶段载荷。 */
                    ToolExecutionEvent tool = null;
                    if (type == AgentEventType.TOOL_EXECUTION) {
                        tool = new ToolExecutionEvent(payload.path("callId").asText(),
                                payload.path("toolName").asText(),
                                ToolExecutionPhase.valueOf(payload.path("phase").asText()),
                                payload.has("toolResult") ? decodeToolResult(payload.path("toolResult")) : null);
                    }
                    /** 用量载荷。 */
                    ModelTokenUsage usage = payload.has("usage") ? decodeUsage(payload.path("usage")) : null;
                    /** 计划载荷。 */
                    PlanEvent plan = null;
                    if (payload.has("plan")) {
                        /** 当前计划 JSON。 */
                        JsonNode node = payload.path("plan");
                        plan = new PlanEvent(node.path("planName").asText(), node.path("stepNumber").asInt(),
                                node.path("stepCount").asInt(), node.path("status").isNull() ? null
                                : PlanStepStatus.valueOf(node.path("status").asText()), node.path("detail").asText());
                    }
                    events.add(new AgentEvent(runId, rows.getString(1), rows.getLong(2),
                            rows.getTimestamp(5).toInstant(), type,
                            payload.has("text") ? payload.path("text").asText() : null,
                            payload.has("result") ? decodeResult(payload.path("result")) : null,
                            tool, usage, plan));
                }
            }
            return List.copyOf(events);
        } catch (Exception exception) {
            throw new IllegalStateException("PERSISTENCE_ERROR", exception);
        }
    }

    /**
     * 从受控 JSON 重建最终结果。
     *
     * @param node 结果节点
     * @return 运行结果
     */
    private static AgentResult decodeResult(JsonNode node) {
        /** 错误节点。 */
        JsonNode errorNode = node.path("error");
        return new AgentResult(node.path("runId").asText(), node.path("sessionId").asText(),
                node.path("requestId").asText(), AgentResultStatus.valueOf(node.path("status").asText()),
                textOrNull(node, "finalText"), node.path("finishReason").isNull() ? null
                : ModelFinishReason.valueOf(node.path("finishReason").asText()),
                errorNode.isObject() ? new AgentError(errorNode.path("code").asText(),
                        errorNode.path("message").asText()) : null,
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
     * 读取可空字符串值。
     *
     * @param node 父节点
     * @param field 字段名称
     * @return 文本或 null
     */
    private static String textOrNull(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
    }

    /**
     * 查询运行中是否记录过任何工具，供恢复策略判断。
     *
     * @param runId 运行标识
     * @return 有工具意图时为 true
     */
    public boolean hasToolExecutions(String runId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM agent_tool_execution WHERE run_id=?")) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1) > 0;
            }
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 计算工具参数的稳定 SHA-256 哈希。
     *
     * @param value 参数 JSON
     * @return 小写十六进制哈希
     */
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * 转换数据库错误。
     *
     * @param exception JDBC 错误
     * @return 稳定类别的运行时错误
     */
    private static IllegalStateException persistence(SQLException exception) {
        return new IllegalStateException("PERSISTENCE_ERROR", exception);
    }

}
