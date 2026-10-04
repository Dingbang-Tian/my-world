package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * 只读检查工具的参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class SubAgentExecutionTestReadParameters {
    /**
     * 待读文件路径。
     */
    @ToolParam(description = "待读文件路径")
    public String path;
}
