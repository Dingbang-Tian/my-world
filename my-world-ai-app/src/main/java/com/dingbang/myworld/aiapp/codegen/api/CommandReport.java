package com.dingbang.myworld.aiapp.codegen.api;

import java.util.Objects;

/**
 * 记录一次命令工具的执行目录、退出状态和有界输出。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public record CommandReport(String runId, String command, String cwd, String shell, String status,
                            Integer exitCode, String output, boolean truncated) {
    /**
     * 校验命令报告的必需字段。
     *
     * @param runId Agent 运行标识
     * @param command 执行的命令文本
     * @param cwd 工作目录内的相对执行目录
     * @param shell 实际使用的 shell
     * @param status COMPLETED、TIMED_OUT 或 CANCELLED
     * @param exitCode 正常完成时的退出码，其余情况为 null
     * @param output 最多保留 16000 字节的合并输出
     * @param truncated 输出是否截断
     */
    public CommandReport {
        Objects.requireNonNull(runId, "运行标识不能为空");
        Objects.requireNonNull(command, "命令不能为空");
        Objects.requireNonNull(cwd, "执行目录不能为空");
        Objects.requireNonNull(shell, "shell 不能为空");
        Objects.requireNonNull(status, "状态不能为空");
        Objects.requireNonNull(output, "输出不能为空");
    }
}
