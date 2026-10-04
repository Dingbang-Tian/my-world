package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * 按字节偏移读取外置命令输出的模型参数。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
public final class ReadToolOutputArgs {
    /** 命令结果返回的输出标识。 */
    @ToolParam(description = "execute_command 返回的 outputId", required = true)
    public String outputId;
    /** 起始字节偏移，默认零。 */
    @ToolParam(description = "起始字节偏移，默认 0", required = false)
    public Long offset;
    /** 单次最多读取字节数，默认 4096，上限 8192。 */
    @ToolParam(description = "本次读取字节数，默认 4096，最多 8192", required = false)
    public Integer limit;
}
