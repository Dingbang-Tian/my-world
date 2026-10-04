package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * EditArgs 的模型参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class FileEditArgs {
    /**
     * 已有文件相对路径。
     */
    @ToolParam(description = "已有文件相对路径", required = true)
    public String path;
    /**
     * 读取时得到的 SHA-256 小写十六进制。
     */
    @ToolParam(description = "读取时得到的 SHA-256 小写十六进制", required = true)
    public String expectedHash;
    /**
     * 编辑模式。
     */
    @ToolParam(description = "编辑模式：replace、insert 或 append", required = true)
    public String mode;
    /**
     * 新内容或插入内容。
     */
    @ToolParam(description = "新内容或插入内容", required = true)
    public String content;
    /**
     * 替换模式下唯一匹配的旧文本。
     */
    @ToolParam(description = "替换模式下唯一匹配的旧文本", required = false)
    public String oldText;
    /**
     * 插入模式下的行号，从 1 开始。
     */
    @ToolParam(description = "插入模式下的行号，从 1 开始", required = false)
    public Integer line;
    /**
     * 替换范围起始行，或插入模式下的行号。
     */
    @ToolParam(description = "替换范围起始行，或插入行号", required = false)
    public Integer startLine;
    /**
     * 替换范围结束行，包含本行。
     */
    @ToolParam(description = "替换范围结束行", required = false)
    public Integer endLine;
}
