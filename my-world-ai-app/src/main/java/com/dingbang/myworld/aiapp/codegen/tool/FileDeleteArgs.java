package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * DeleteArgs 的模型参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class FileDeleteArgs {
    /**
     * 待删除普通文件相对路径。
     */
    @ToolParam(description = "待删除普通文件相对路径", required = true)
    public String path;
    /**
     * 文件读取时得到的 SHA-256。
     */
    @ToolParam(description = "文件读取时得到的 SHA-256", required = true)
    public String expectedHash;
}
