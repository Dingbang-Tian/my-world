package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * ViewArgs 的模型参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class FileViewArgs {
    /**
     * 相对文件路径。
     */
    @ToolParam(description = "相对文件路径", required = true)
    public String path;
    /**
     * 起始行号，从 1 开始。
     */
    @ToolParam(description = "起始行号，从 1 开始", required = false)
    public Integer startLine;
    /**
     * 结束行号，包含本行。
     */
    @ToolParam(description = "结束行号，包含本行", required = false)
    public Integer endLine;
}
