package com.dingbang.myworld.aiapp.codegen.api;

import lombok.Value;

import java.util.Objects;

/**
 * 记录一次命令工具的执行目录、退出状态和有界首尾预览。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Value
public class CommandReport {

    /**
     * Agent 运行标识。
     */
    String runId;

    /**
     * 调用方请求执行的命令文本。
     */
    String command;

    /**
     * 工作区内的相对执行目录。
     */
    String cwd;

    /**
     * 实际执行命令的 shell。
     */
    String shell;

    /**
     * COMPLETED、TIMED_OUT、CANCELLED 或 FAILED。
     */
    String status;

    /**
     * 正常结束时的退出码，其余情况为空。
     */
    Integer exitCode;

    /**
     * 首尾合并预览，完整输出由 outputId 在会话工具结果中定位。
     */
    String output;

    /**
     * 输出是否因达到限制而截断。
     */
    boolean truncated;
    /**
     * 校验命令报告的必需字段。
     *
     * @param runId Agent 运行标识
     * @param command 执行的命令文本
     * @param cwd 工作目录内的相对执行目录
     * @param shell 实际使用的 shell
     * @param status COMPLETED、TIMED_OUT 或 CANCELLED
     * @param exitCode 正常完成时的退出码，其余情况为 null
     * @param output 最多保留约 4096 字节的首尾预览
     * @param truncated 输出是否截断
     */
    public CommandReport(String runId, String command, String cwd, String shell, String status,
                         Integer exitCode, String output, boolean truncated) {
        Objects.requireNonNull(runId, "运行标识不能为空");
        Objects.requireNonNull(command, "命令不能为空");
        Objects.requireNonNull(cwd, "执行目录不能为空");
        Objects.requireNonNull(shell, "shell 不能为空");
        Objects.requireNonNull(status, "状态不能为空");
        Objects.requireNonNull(output, "输出不能为空");
        this.runId = runId;
        this.command = command;
        this.cwd = cwd;
        this.shell = shell;
        this.status = status;
        this.exitCode = exitCode;
        this.output = output;
        this.truncated = truncated;
    }
}
