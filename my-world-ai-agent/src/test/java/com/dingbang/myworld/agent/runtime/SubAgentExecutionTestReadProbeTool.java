package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;

/**
 * 返回固定检查结果的只读工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@ToolInfo(name = "read_probe", description = "只读检查文件")
public final class SubAgentExecutionTestReadProbeTool implements Tool<SubAgentExecutionTestReadParameters> {
    /**
     * 返回工具参数类型。
     *
     * @return 参数类
     */
    @Override
    public Class<SubAgentExecutionTestReadParameters> parameterType() {
        return SubAgentExecutionTestReadParameters.class;
    }

    /**
     * 返回固定的只读检查结果。
     *
     * @param parameters 文件参数
     * @param context 可信运行上下文
     * @return 检查文本
     */
    @Override
    public ToolExecutionResult execute(SubAgentExecutionTestReadParameters parameters, ToolExecutionContext context) {
        context.checkActive();
        return ToolExecutionResult.text(parameters.path + " 源文件正常");
    }
}
