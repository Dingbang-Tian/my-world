package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import java.util.List;

/**
 * SearchFilesArgs 的模型参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class FileSearchArgs {
    /**
     * 相对目录路径。
     */
    @ToolParam(description = "相对目录路径", required = false)
    public String path;
    /**
     * 文件名关键词，匹配任一项。
     */
    @ToolParam(description = "文件名关键词，匹配任一项", required = true)
    public List<String> keywords;
}
