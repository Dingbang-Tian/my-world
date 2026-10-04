package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;

/**
 * 固定返回产物名称的测试工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@ToolInfo(name = "artifact", description = "返回生成文件名")
public final class PlanExecutionTestArtifactTool implements Tool<PlanExecutionTestArtifactParameters> {
    /**
     * 返回工具参数类。
     *
     * @return 参数类
     */
    @Override
    public Class<PlanExecutionTestArtifactParameters> parameterType() {
        return PlanExecutionTestArtifactParameters.class;
    }

    /**
     * 返回产物名称。
     *
     * @param parameters 已解析文件名
     * @param context 可信运行上下文
     * @return 文件名
     */
    @Override
    public ToolExecutionResult execute(PlanExecutionTestArtifactParameters parameters, ToolExecutionContext context) {
        context.checkActive();
        return ToolExecutionResult.text(parameters.name);
    }
}
