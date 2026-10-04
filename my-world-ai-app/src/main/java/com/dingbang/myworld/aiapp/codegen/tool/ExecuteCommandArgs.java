package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * 模型可填写的命令参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class ExecuteCommandArgs {
    /**
     * 要执行的命令文本。
     */
    @ToolParam(description = "要执行的命令", required = true)
    public String command;
    /**
     * 相对于工作目录的执行目录。
     */
    @ToolParam(description = "执行目录相对路径，默认工作目录", required = false)
    public String cwd;
    /**
     * auto、bash 或 powershell。
     */
    @ToolParam(description = "shell：auto、bash 或 powershell，默认 auto", required = false)
    public String shell;
}
