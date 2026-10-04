package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;

/**
 * 表示父运行未授权的危险工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@ToolInfo(name = "danger_probe", description = "测试未授权能力")
public final class SubAgentExecutionTestDangerProbeTool implements Tool<SubAgentExecutionTestReadParameters> {
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
     * 若运行会到达此处则返回标记。
     *
     * @param parameters 文件参数
     * @param context 可信运行上下文
     * @return 标记文本
     */
    @Override
    public ToolExecutionResult execute(SubAgentExecutionTestReadParameters parameters, ToolExecutionContext context) {
        return ToolExecutionResult.text("UNAUTHORIZED");
    }
}
