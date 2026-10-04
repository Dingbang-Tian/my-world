package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * MoveArgs 的模型参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class FileMoveArgs {
    /**
     * 源文件相对路径。
     */
    @ToolParam(description = "源文件相对路径，与 source 二选一", required = false)
    public String path;
    /**
     * 目标文件相对路径。
     */
    @ToolParam(description = "目标文件相对路径，与 target 二选一", required = false)
    public String targetPath;
    /**
     * 参考工具使用的源路径字段。
     */
    @ToolParam(description = "源文件相对路径，与 path 二选一", required = false)
    public String source;
    /**
     * 参考工具使用的目标路径字段。
     */
    @ToolParam(description = "目标文件相对路径，与 targetPath 二选一", required = false)
    public String target;
    /**
     * 源文件读取时得到的 SHA-256。
     */
    @ToolParam(description = "源文件读取时得到的 SHA-256", required = true)
    public String expectedHash;
}
