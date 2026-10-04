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
     * Java 声明名称，与行范围互斥；重载时返回候选行号。
     */
    @ToolParam(description = "Java 类/方法/构造器/字段名，如 execute 或 Outer.Inner#execute；与行范围互斥，重名返回候选行号", required = false)
    public String symbol;
    /**
     * 起始行号，从 1 开始。
     */
    @ToolParam(description = "起始行号，从 1 开始；建议与 endLine 一起使用", required = false)
    public Integer startLine;
    /**
     * 结束行号，包含本行。
     */
    @ToolParam(description = "结束行号，包含本行；不填写时最多返回从 startLine 开始的 240 行", required = false)
    public Integer endLine;
}
