package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolInfo;

/**
 * 列表和枚举测试工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@ToolInfo(name = "filter", description = "按模式筛选标签")
public class ToolExecutorTestFilterTool implements Tool<ToolExecutorTestFilterArgs> {
    /**
     * 返回筛选参数类。
     *
     * @return 参数类
     */
    @Override
    public Class<ToolExecutorTestFilterArgs> parameterType() { return ToolExecutorTestFilterArgs.class; }

    /**
     * 返回测试输出。
     *
     * @param parameters 筛选参数
     * @param context 可信上下文
     * @return 文本输出
     */
    @Override
    public ToolExecutionResult execute(ToolExecutorTestFilterArgs parameters, ToolExecutionContext context) {
        return ToolExecutionResult.text(parameters.mode.name());
    }
}
