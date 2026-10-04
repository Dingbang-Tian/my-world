package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import java.util.List;

/**
 * SearchInFileArgs 的模型参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class FileSearchInFileArgs {
    /**
     * 相对文件路径。
     */
    @ToolParam(description = "相对文件路径", required = true)
    public String path;
    /**
     * 内容关键词，匹配任一项。
     */
    @ToolParam(description = "内容关键词，匹配任一项；与 patterns 二选一", required = false)
    public List<String> keywords;
    /**
     * 参考工具使用的搜索词字段。
     */
    @ToolParam(description = "内容关键词，与 keywords 二选一", required = false)
    public List<String> patterns;
    /**
     * 匹配行前后显示的行数。
     */
    @ToolParam(description = "匹配行前后显示的行数", required = false)
    public Integer contextLines;
}
