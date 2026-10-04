package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.common.utils.lang.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 在授权注册表内校验并执行单次模型工具调用。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public final class ToolExecutor {
    /**
     * 工具执行审计日志入口。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutor.class);
    /**
     * 失败消息摘要的最大日志长度。
     */
    private static final int MAX_ERROR_PREVIEW = 240;
    /**
     * 当前运行获授权的工具快照。
     */
    private final ToolRegistry registry;

    /**
     * 绑定运行级授权注册表。
     *
     * @param registry 已冻结的工具注册表
     */
    public ToolExecutor(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "工具注册表不能为 null");
    }

    /**
     * 校验调用并执行工具；普通失败转换为可回传模型的结构化结果。
     *
     * @param call 模型提出的工具调用
     * @param context 可信执行上下文
     * @param listener 阶段监听器，可为 null
     * @return 与调用标识配对的工具结果
     * @throws ExecutionControlException 取消或超时时直接上抛，不转换为普通工具错误
     */
    public ToolResult execute(ToolCall call, ToolExecutionContext context, ToolExecutionListener listener) {
        Objects.requireNonNull(call, "工具调用不能为 null");
        Objects.requireNonNull(context, "执行上下文不能为 null");
        context.checkActive();
        logCall(call, context);
        emit(call, ToolExecutionPhase.PREPARING, null, listener);
        // 工具对象和描述必须来自同一个可信注册表快照。
        Tool<?> tool = registry.findTool(call.getName());
        ToolDescriptor<?> descriptor = registry.findDescriptor(call.getName());
        if (tool == null || descriptor == null) {
            return failed(call, context, "TOOL_NOT_FOUND", "工具未获授权或未注册: " + call.getName(), listener);
        }
        // 在进入业务工具前完成 JSON 解析和参数约束校验。
        Object parameters;
        try {
            parameters = descriptor.parse(call.getArgumentsJson());
        } catch (IllegalArgumentException exception) {
            return failed(call, context, "TOOL_VALIDATION_ERROR", exception.getMessage(), listener);
        }
        context.checkActive();
        emit(call, ToolExecutionPhase.CALLING, null, listener);
        try {
            // 工具成功后再次检查取消信号，避免把取消后的迟到输出报告为成功。
            ToolExecutionResult output = invoke(tool, parameters, context);
            context.checkActive();
            // ToolResult 始终保留原 callId，下一轮模型据此完成消息配对。
            ToolResult result = new ToolResult(call.getCallId(), ToolResultStatus.SUCCESS,
                    output.getContent(), null, output.isTruncated());
            logResult(call, context, result);
            emit(call, ToolExecutionPhase.COMPLETED, result, listener);
            return result;
        } catch (ExecutionControlException exception) {
            throw exception;
        } catch (Exception exception) {
            context.checkActive();
            return failed(call, context, "TOOL_EXECUTION_ERROR", "工具执行失败: " + exception.getMessage(), listener);
        }
    }

    /**
     * 在注册表已核对参数类后调用工具。
     *
     * @param tool 注册工具
     * @param parameters 已解析参数
     * @param context 可信上下文
     * @return 工具输出
     * @param <P> 工具参数类型
     */
    private static <P> ToolExecutionResult invoke(Tool<P> tool, Object parameters, ToolExecutionContext context) {
        // 注册阶段已经检查参数类型，这里只做安全强制转换。
        P typed = tool.parameterType().cast(parameters);
        context.checkActive();
        return Objects.requireNonNull(tool.execute(typed, context), "工具不能返回 null 结果");
    }

    /**
     * 创建普通工具失败结果并发布失败事件。
     *
     * @param call 模型调用
     * @param context 可信执行上下文
     * @param code 稳定错误码
     * @param message 错误说明
     * @param listener 阶段监听器
     * @return 失败结果
     */
    private static ToolResult failed(ToolCall call, ToolExecutionContext context, String code, String message,
                                     ToolExecutionListener listener) {
        // 普通工具错误作为结构化 TOOL 消息返回，允许模型修正参数。
        ToolResult result = new ToolResult(call.getCallId(), ToolResultStatus.ERROR, message, code, false);
        logResult(call, context, result);
        emit(call, ToolExecutionPhase.FAILED, result, listener);
        return result;
    }

    /**
     * 记录工具调用的安全参数摘要，不输出完整参数内容。
     *
     * @param call 模型工具调用
     * @param context 可信执行上下文
     */
    private static void logCall(ToolCall call, ToolExecutionContext context) {
        String arguments = call.getArgumentsJson();
        LOGGER.info("agent_tool_call runId={} sessionId={} callId={} tool={} argumentsLength={} argumentsHash={}",
                safe(context.getRunId()), safe(context.getSessionId()), safe(call.getCallId()), safe(call.getName()),
                arguments == null ? 0 : arguments.length(), arguments == null ? null : HashUtils.sha256(arguments));
    }

    /**
     * 记录工具结果的安全摘要，并在失败时附加有限长度的错误说明。
     *
     * @param call 模型工具调用
     * @param context 可信执行上下文
     * @param result 工具结果
     */
    private static void logResult(ToolCall call, ToolExecutionContext context, ToolResult result) {
        String content = result.getContent();
        LOGGER.info("agent_tool_result runId={} sessionId={} callId={} tool={} status={} errorCode={} "
                        + "contentLength={} contentHash={} truncated={} errorMessage={}",
                safe(context.getRunId()), safe(context.getSessionId()), safe(call.getCallId()), safe(call.getName()),
                result.getStatus(), safe(result.getErrorCode()), content.length(), HashUtils.sha256(content),
                result.isTruncated(), result.getStatus() == ToolResultStatus.ERROR ? preview(content) : null);
    }

    /**
     * 将错误文本压缩为单行有限摘要，避免日志被工具输出污染。
     *
     * @param value 原始文本
     * @return 单行摘要
     */
    private static String preview(String value) {
        String sanitized = value == null ? null : value.replaceAll("[\\r\\n\\t]", " ");
        if (sanitized == null) return null;
        return sanitized.substring(0, Math.min(MAX_ERROR_PREVIEW, sanitized.length()));
    }

    /**
     * 将外部标识限制为单行日志字段。
     *
     * @param value 外部标识
     * @return 安全日志值
     */
    private static String safe(String value) {
        if (value == null) return null;
        String sanitized = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 120));
    }

    /**
     * 隔离观察者异常，确保事件回调不能改变工具结果。
     *
     * @param call 模型调用
     * @param phase 当前阶段
     * @param result 当前结果，可为 null
     * @param listener 阶段监听器，可为 null
     */
    private static void emit(ToolCall call, ToolExecutionPhase phase, ToolResult result,
                             ToolExecutionListener listener) {
        if (listener == null) {
            return;
        }
        try {
            listener.onEvent(new ToolExecutionEvent(call.getCallId(), call.getName(), phase, result));
        } catch (RuntimeException ignored) {
            // 观察者不参与工具执行或错误分类。
        }
    }
}
