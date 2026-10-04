package com.dingbang.myworld.agent.tool;


/**
 * 不依赖工具类注解的显式描述工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public class ToolExecutorTestExplicitTool implements Tool<ToolExecutorTestExplicitArgs> {
    /**
     * 返回参数类型。
     *
     * @return 参数类
     */
    @Override
    public Class<ToolExecutorTestExplicitArgs> parameterType() { return ToolExecutorTestExplicitArgs.class; }

    /**
     * 显式提供工具名称与说明。
     *
     * @return 工具描述
     */
    @Override
    public ToolDescriptor<ToolExecutorTestExplicitArgs> descriptor() {
        return ToolDescriptor.of("explicit", "验证多类型参数", ToolExecutorTestExplicitArgs.class);
    }

    /**
     * 返回接收到的标题和位置数量。
     *
     * @param parameters 多类型参数
     * @param context 可信上下文
     * @return 测试输出
     */
    @Override
    public ToolExecutionResult execute(ToolExecutorTestExplicitArgs parameters, ToolExecutionContext context) {
        return ToolExecutionResult.text(parameters.title + ":" + parameters.offsets.size());
    }
}
