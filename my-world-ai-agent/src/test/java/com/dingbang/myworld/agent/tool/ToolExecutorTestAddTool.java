package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolInfo;

/**
 * 测试用加法工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@ToolInfo(name = "add", description = "计算两个整数之和")
public class ToolExecutorTestAddTool implements Tool<ToolExecutorTestAddArgs> {
    /**
     * 已实际调用次数。
     */
    int invocations;

    /**
     * 返回参数类型。
     *
     * @return 加法参数类
     */
    @Override
    public Class<ToolExecutorTestAddArgs> parameterType() {
        return ToolExecutorTestAddArgs.class;
    }

    /**
     * 计算整数之和并显示可信运行标识。
     *
     * @param parameters 两个加数
     * @param context 可信运行上下文
     * @return 计算结果
     */
    @Override
    public ToolExecutionResult execute(ToolExecutorTestAddArgs parameters, ToolExecutionContext context) {
        invocations++;
        return ToolExecutionResult.text((parameters.a + parameters.b) + "@" + context.getRunId());
    }
}
