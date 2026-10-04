package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * CreateArgs 的模型参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class FileCreateArgs {
    /**
     * 新文件相对路径。
     */
    @ToolParam(description = "新文件相对路径", required = true)
    public String path;
    /**
     * UTF-8 文件内容，可为空字符串。
     */
    @ToolParam(description = "UTF-8 文件内容，可为空字符串", required = true)
    public String content;
}
