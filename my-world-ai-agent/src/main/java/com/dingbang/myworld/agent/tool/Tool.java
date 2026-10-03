package com.dingbang.myworld.agent.tool;

/**
 * 接收已校验模型参数与可信执行上下文的 Java 工具。
 *
 * @author Sebastian
 * @since 2026/10/02
 * @param <P> 工具参数类型
 */
public interface Tool<P> {

    /**
     * 显式给出参数类型，避免运行时泛型擦除造成类型猜测。
     *
     * @return 参数类
     */
    Class<P> parameterType();

    /**
     * 执行工具。
     *
     * @param parameters 已校验的模型参数
     * @param context 可信运行上下文
     * @return 工具输出
     */
    ToolExecutionResult execute(P parameters, ToolExecutionContext context);

    /**
     * 声明执行失败时是否可能留下不能由 Java 异常判断的外部副作用。
     *
     * @return 文件写入、命令等工具返回 true
     */
    default boolean mayHaveExternalSideEffects() {
        return false;
    }

    /**
     * 根据工具类与参数字段注解生成默认描述；实现类也可覆盖以显式提供描述。
     *
     * @return 工具描述
     */
    default ToolDescriptor descriptor() {
        return ToolDescriptor.fromAnnotations(getClass(), parameterType());
    }
}
