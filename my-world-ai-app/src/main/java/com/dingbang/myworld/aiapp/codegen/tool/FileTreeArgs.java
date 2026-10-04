package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * TreeArgs 的模型参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class FileTreeArgs {
    /**
     * 相对目录路径。
     */
    @ToolParam(description = "相对目录路径", required = false)
    public String path;
    /**
     * 最大目录深度。
     */
    @ToolParam(description = "最大目录深度", required = false)
    public Integer depth;
    /**
     * 参考工具使用的最大目录深度字段。
     */
    @ToolParam(description = "最大目录深度，与 depth 二选一", required = false)
    public Integer maxDepth;
}
