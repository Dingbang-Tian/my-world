package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;

import java.util.Objects;

/**
 * 在授权注册表内校验并执行单次模型工具调用。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public final class ToolExecutor {
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
        emit(call, ToolExecutionPhase.PREPARING, null, listener);
        /** 注册表内的可信 Java 工具。 */
        Tool<?> tool = registry.findTool(call.getName());
        /** 与工具配对的参数契约。 */
        ToolDescriptor<?> descriptor = registry.findDescriptor(call.getName());
        if (tool == null || descriptor == null) {
            return failed(call, "TOOL_NOT_FOUND", "工具未获授权或未注册: " + call.getName(), listener);
        }
        /** 经 JSON Schema 规则校验的参数对象。 */
        Object parameters;
        try {
            parameters = descriptor.parse(call.getArgumentsJson());
        } catch (IllegalArgumentException exception) {
            return failed(call, "TOOL_VALIDATION_ERROR", exception.getMessage(), listener);
        }
        context.checkActive();
        emit(call, ToolExecutionPhase.CALLING, null, listener);
        try {
            /** Java 工具成功输出。 */
            ToolExecutionResult output = invoke(tool, parameters, context);
            context.checkActive();
            /** 与模型调用标识配对的成功结果。 */
            ToolResult result = new ToolResult(call.getCallId(), ToolResultStatus.SUCCESS,
                    output.getContent(), null, output.isTruncated());
            emit(call, ToolExecutionPhase.COMPLETED, result, listener);
            return result;
        } catch (ExecutionControlException exception) {
            throw exception;
        } catch (Exception exception) {
            context.checkActive();
            return failed(call, "TOOL_EXECUTION_ERROR", "工具执行失败: " + exception.getMessage(), listener);
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
        /** 与工具类型声明匹配的参数。 */
        P typed = tool.parameterType().cast(parameters);
        context.checkActive();
        return Objects.requireNonNull(tool.execute(typed, context), "工具不能返回 null 结果");
    }

    /**
     * 创建普通工具失败结果并发布失败事件。
     *
     * @param call 模型调用
     * @param code 稳定错误码
     * @param message 错误说明
     * @param listener 阶段监听器
     * @return 失败结果
     */
    private static ToolResult failed(ToolCall call, String code, String message, ToolExecutionListener listener) {
        /** 与模型调用标识配对的失败结果。 */
        ToolResult result = new ToolResult(call.getCallId(), ToolResultStatus.ERROR, message, code, false);
        emit(call, ToolExecutionPhase.FAILED, result, listener);
        return result;
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
