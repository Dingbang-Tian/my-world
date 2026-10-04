package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * 测试产物工具参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class PlanExecutionTestArtifactParameters {
    /**
     * 产物文件名。
     */
    @ToolParam(description = "产物文件名")
    public String name;
}
