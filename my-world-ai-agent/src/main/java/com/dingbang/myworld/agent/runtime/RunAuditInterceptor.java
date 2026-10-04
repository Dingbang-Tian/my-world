package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.tool.ToolExecutionEvent;
import com.dingbang.myworld.agent.tool.ToolExecutionListener;
import com.dingbang.myworld.agent.tool.ToolExecutionPhase;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.api.ModelTokenUsage;
import com.dingbang.myworld.common.utils.lang.HashUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 集中记录运行、模型和工具的安全审计元数据与耗时。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class RunAuditInterceptor implements ToolExecutionListener {
    /**
     * 运行审计专用日志入口。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RunAuditInterceptor.class);
    /**
     * 本次运行的根追踪标识。
     */
    private final String traceId;
    /**
     * 本次运行标识。
     */
    private final String runId;
    /**
     * 当前会话标识。
     */
    private final String sessionId;
    /**
     * 父运行标识，可为空。
     */
    private final String parentRunId;
    /**
     * 运行开始的单调时间。
     */
    private final long startedNanos = System.nanoTime();
    /**
     * 工具调用开始的单调时间，按调用标识隔离。
     */
    private final Map<String, Long> toolStartedNanos = new HashMap<>();
    /**
     * 已开始的模型调用数。
     */
    private int modelCalls;
    /**
     * 实际收到用量的模型调用数。
     */
    private int reportedUsageCalls;
    /**
     * 可信进程级调试日志开关；调试内容仍只允许安全元数据。
     */
    private final boolean debugEnabled = Boolean.getBoolean("myworld.agent.debug");

    /**
     * 记录不含提示词、密钥与工作目录的请求配置快照。
     *
     * @param request 本次请求
     * @param definition 可信定义
     * @param options 本次实际采用的生成选项
     * @param promptHash 模板内容哈希
     */
    void started(AgentRequest request, AgentDefinition definition, ModelOptions options, String promptHash) {
        LOGGER.info("agent_run event=start traceId={} runId={} parentRunId={} sessionId={} requestHash={} "
                        + "modelId={} promptHash={} temperature={} maxCompletionTokens={} reasoningEffort={} "
                        + "thinkingEnabled={} maxModelTurns={} maxToolCalls={} maxOutputCharacters={} timeoutMillis={}",
                traceId, runId, parentRunId, safe(sessionId),
                HashUtils.sha256(request.getRequestId()), safe(definition.getModelId()), promptHash,
                options.getTemperature(), options.getMaxCompletionTokens(), safe(options.getReasoningEffort()),
                options.getThinkingEnabled(), definition.getLimits().getMaxModelTurns(),
                definition.getLimits().getMaxToolCalls(), definition.getLimits().getMaxOutputCharacters(),
                definition.getLimits().getTimeout().toMillis());
        if (debugEnabled) {
            LOGGER.debug("agent_debug traceId={} runId={} requestHash={} content=[REDACTED] toolOutput=[REDACTED]",
                    traceId, runId, HashUtils.sha256(request.getRequestId()));
        }
    }

    /**
     * 记录一次模型调用开始，使终态能够区分缺失用量。
     *
     * @param callId 模型调用标识
     */
    synchronized void modelStarted(String callId) {
        modelCalls++;
        LOGGER.info("agent_model event=start traceId={} runId={} parentRunId={} callId={} modelCallNumber={}",
                traceId, runId, parentRunId, safe(callId), modelCalls);
    }

    /**
     * 记录模型调用的耗时和结果，不记录请求或响应内容。
     *
     * @param callId 模型调用标识
     * @param elapsedNanos 单调耗时
     * @param outcome 成功或稳定失败类别
     */
    synchronized void modelFinished(String callId, long elapsedNanos, String outcome) {
        LOGGER.info("agent_model traceId={} runId={} parentRunId={} callId={} durationMillis={} outcome={}",
                traceId, runId, parentRunId, safe(callId), millis(elapsedNanos), safe(outcome));
    }

    /**
     * 记录一次运行中上下文压缩前后的估算值。
     *
     * @param turnNumber 当前模型回合
     * @param before 压缩前估算值
     * @param after 压缩后估算值
     * @param capacity 当前输入容量
     */
    synchronized void contextCompacted(int turnNumber, int before, int after, int capacity) {
        LOGGER.info("agent_context event=compacted traceId={} runId={} turnNumber={} before={} after={} capacity={}",
                traceId, runId, turnNumber, before, after, capacity);
    }

    /**
     * 记录供应商实际报告的用量；缺失时由终态日志明确标为 unknown。
     *
     * @param callId 模型调用标识
     * @param usage 单次供应商用量
     */
    synchronized void usage(String callId, ModelTokenUsage usage) {
        reportedUsageCalls++;
        LOGGER.info("agent_usage traceId={} runId={} callId={} inputTokens={} outputTokens={} totalTokens={}",
                traceId, runId, safe(callId), usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /**
     * 集中审计工具阶段和耗时，仅记录工具名、结果状态与错误码。
     *
     * @param event 工具执行阶段
     */
    @Override
    public synchronized void onEvent(ToolExecutionEvent event) {
        if (event.getPhase() == ToolExecutionPhase.PREPARING) {
            toolStartedNanos.put(event.getCallId(), System.nanoTime());
        }
        // 终态事件负责结束耗时统计，其他阶段只保留开始时间。
        boolean finished = event.getPhase() == ToolExecutionPhase.COMPLETED
                || event.getPhase() == ToolExecutionPhase.FAILED;
        // 开始时间缺失表示只观察到了终态，此时不伪造耗时。
        Long start = finished ? toolStartedNanos.remove(event.getCallId()) : toolStartedNanos.get(event.getCallId());
        LOGGER.info("agent_tool traceId={} runId={} parentRunId={} callId={} tool={} phase={} durationMillis={} "
                        + "resultStatus={} errorCode={}", traceId, runId, parentRunId, safe(event.getCallId()),
                safe(event.getToolName()), event.getPhase(), start == null || !finished ? null : millis(System.nanoTime() - start),
                event.getResult() == null ? null : event.getResult().getStatus(),
                event.getResult() == null ? null : safe(event.getResult().getErrorCode()));
    }

    /**
     * 记录最终状态、限额错误与用量状态，并清除未结束的工具计时。
     *
     * @param result 最终运行结果
     */
    synchronized void finished(AgentResult result) {
        toolStartedNanos.clear();
        // 用 null、partial、reported 区分未报告、部分报告和完整报告。
        ModelTokenUsage usage = result.getUsage();
        LOGGER.info("agent_run event=finish traceId={} runId={} parentRunId={} status={} errorCode={} "
                        + "errorMessage={} durationMillis={} modelCalls={} reportedUsageCalls={} usageStatus={} "
                        + "inputTokens={} outputTokens={} totalTokens={}",
                traceId, runId, parentRunId, result.getStatus(),
                result.getError() == null ? null : safe(result.getError().getCode()),
                result.getError() == null ? null : preview(result.getError().getMessage()),
                millis(System.nanoTime() - startedNanos), modelCalls, reportedUsageCalls, usage == null ? "unknown"
                        : reportedUsageCalls < modelCalls ? "partial" : "reported",
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens());
    }

    /**
     * 将纳秒转为非负整毫秒。
     *
     * @param nanos 单调耗时
     * @return 整毫秒
     */
    private static long millis(long nanos) {
        return Math.max(0, nanos / 1_000_000);
    }

    /**
     * 将外部标识限制为单行的有界日志值。
     *
     * @param value 外部标识，可为空
     * @return 可安全放入日志字段的值
     */
    private static String safe(String value) {
        if (value == null) return null;
        // 替换换行和特殊字符，避免外部标识破坏 key=value 日志结构。
        String sanitized = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 120));
    }

    /**
     * 将错误文本压缩为单行有限摘要，避免异常内容破坏日志结构。
     *
     * @param value 原始错误文本
     * @return 单行错误摘要
     */
    private static String preview(String value) {
        if (value == null) return null;
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ");
        return sanitized.substring(0, Math.min(sanitized.length(), 240));
    }
}
