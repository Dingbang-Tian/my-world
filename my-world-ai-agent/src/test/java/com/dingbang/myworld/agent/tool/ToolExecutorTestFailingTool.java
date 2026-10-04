package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolInfo;

/**
 * 不含参数的失败测试工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@ToolInfo(name = "fail", description = "主动失败")
public class ToolExecutorTestFailingTool implements Tool<ToolExecutorTestEmptyArgs> {
    /**
     * 返回空参数类型。
     *
     * @return 空参数类
     */
    @Override
    public Class<ToolExecutorTestEmptyArgs> parameterType() {
        return ToolExecutorTestEmptyArgs.class;
    }

    /**
     * 模拟工具执行失败。
     *
     * @param parameters 空参数
     * @param context 可信上下文
     * @return 不会返回
     */
    @Override
    public ToolExecutionResult execute(ToolExecutorTestEmptyArgs parameters, ToolExecutionContext context) {
        throw new IllegalStateException("boom");
    }
}
