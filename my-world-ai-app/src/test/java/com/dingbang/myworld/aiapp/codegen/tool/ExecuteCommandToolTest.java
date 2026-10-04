package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.aiframework.api.CancellationToken;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 验证命令结果、输出边界、超时和父取消后的进程清理。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class ExecuteCommandToolTest {
    /**
     * 受控命令使用的临时目录。
     */
    @TempDir
    Path workspace;

    /**
     * 验证成功、失败、空输出、cwd 和环境白名单。
     *
     * @throws Exception 创建目录失败时
     */
    @Test
    void returnsExitCodeOutputAndCwd() throws Exception {
        unixOnly();
        Files.createDirectory(workspace.resolve("nested"));
        // 使用空环境白名单的命令工具。
        ExecuteCommandTool tool = new ExecuteCommandTool(new WorkspacePolicy(workspace), List.of());
        assertThat(run(tool, "pwd", "nested").getContent()).contains("exitCode=0", "nested");
        assertThat(run(tool, "exit 7", "").getContent()).contains("exitCode=7");
        assertThat(run(tool, ":", "").getContent()).isEqualTo("timedOut=false\nexitCode=0\n");
        assertThat(run(tool, "printf '%s' \"$HOME\"", "").getContent())
                .isEqualTo("timedOut=false\nexitCode=0\n");
        assertThatThrownBy(() -> run(tool, "pwd", "../"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("超出工作目录");
        assertThat(ExecuteCommandTool.shellCommand("auto", "pwd")).containsExactly("/bin/bash", "-c", "pwd");
        assertThatThrownBy(() -> ExecuteCommandTool.shellCommand("powershell", "pwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ExecuteCommandTool.shellCommand("auto", "pwd", true))
                .containsExactly("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "pwd");
    }

    /**
     * 验证无换行大输出仍被持续消费且标记截断。
     */
    @Test
    void boundsLongOutputWithoutNewlines() {
        unixOnly();
        // 命令输出。
        ToolExecutionResult result = run(new ExecuteCommandTool(new WorkspacePolicy(workspace), List.of()),
                "printf '%20000s' x", "");
        assertThat(result.getContent()).contains("exitCode=0");
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getContent().length()).isLessThan(16_100);
    }

    /**
     * 验证从启动起计时的超时会停止无换行进程。
     */
    @Test
    void timesOutLongRunningCommand() {
        unixOnly();
        // 使用短测试时限的命令工具。
        ExecuteCommandTool tool = new ExecuteCommandTool(new WorkspacePolicy(workspace), List.of(),
                Duration.ofMillis(200));
        // 超时前的起始时间。
        long start = System.nanoTime();
        // 命令执行结果。
        ToolExecutionResult result = run(tool, "printf 'started'; exec sleep 30", "");
        assertThat(result.getContent()).contains("timedOut=true", "started");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    /**
     * 验证父取消传播到 shell 与其子进程。
     *
     * @throws Exception 等待进程标识或执行结果失败时
     */
    @Test
    void parentCancellationKillsChildProcess() throws Exception {
        unixOnly();
        requireProcessEnumeration();
        // 父运行取消令牌。
        CancellationToken token = new CancellationToken();
        // 命令工具。
        ExecuteCommandTool tool = new ExecuteCommandTool(new WorkspacePolicy(workspace), List.of());
        // 长时间运行的命令。
        ExecuteCommandArgs args = new ExecuteCommandArgs();
        args.command = "sleep 30 & echo $! > child.pid; wait";
        // 后台工具执行。
        CompletableFuture<Void> execution = CompletableFuture.runAsync(() ->
                tool.execute(args, new ToolExecutionContext("run", "session", null, null, token)));
        // 子进程 PID 文件。
        Path pidFile = workspace.resolve("child.pid");
        // 等待子进程标识文件的次数。
        for (int attempt = 0; attempt < 100 && !Files.exists(pidFile); attempt++) {
            Thread.sleep(20);
        }
        assertThat(Files.exists(pidFile)).isTrue();
        // 子进程标识。
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        token.cancel();
        assertThatThrownBy(() -> execution.get(3, TimeUnit.SECONDS))
                .hasCauseInstanceOf(ExecutionControlException.class);
        // 等待子进程退出的次数。
        for (int attempt = 0; attempt < 100 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false); attempt++) {
            Thread.sleep(20);
        }
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    /**
     * 验证局部命令超时同样回收后台子进程。
     *
     * @throws Exception 等待子进程标识失败时
     */
    @Test
    void timeoutKillsChildProcess() throws Exception {
        unixOnly();
        requireProcessEnumeration();
        // 使用短测试时限的命令工具。
        ExecuteCommandTool tool = new ExecuteCommandTool(new WorkspacePolicy(workspace), List.of(),
                Duration.ofMillis(500));
        // 后台子进程的标识文件。
        Path pidFile = workspace.resolve("timeout-child.pid");
        // 命令执行结果。
        ToolExecutionResult result = run(tool, "sleep 30 & echo $! > timeout-child.pid; wait", "");
        assertThat(result.getContent()).contains("timedOut=true");
        assertThat(Files.exists(pidFile)).isTrue();
        // 被回收的子进程标识。
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        // 等待进程状态变化的次数。
        for (int attempt = 0; attempt < 100 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false); attempt++) {
            Thread.sleep(20);
        }
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    /**
     * 验证进程枚举被限制时，取消仍结束直接启动的命令。
     *
     * @throws Exception 等待取消结果失败时
     */
    @Test
    void cancellationStopsDirectProcess() throws Exception {
        unixOnly();
        // 父取消令牌。
        CancellationToken token = new CancellationToken();
        // 执行参数。
        ExecuteCommandArgs args = new ExecuteCommandArgs();
        args.command = "exec sleep 30";
        // 后台执行结果。
        CompletableFuture<Void> execution = CompletableFuture.runAsync(() ->
                new ExecuteCommandTool(new WorkspacePolicy(workspace), List.of()).execute(args,
                        new ToolExecutionContext("run", "session", null, null, token)));
        Thread.sleep(100);
        token.cancel();
        assertThatThrownBy(() -> execution.get(3, TimeUnit.SECONDS))
                .hasCauseInstanceOf(ExecutionControlException.class);
    }

    /**
     * 在给定 cwd 执行命令。
     *
     * @param tool 命令工具
     * @param command 命令
     * @param cwd 相对目录
     * @return 工具结果
     */
    private ToolExecutionResult run(ExecuteCommandTool tool, String command, String cwd) {
        // 模型参数。
        ExecuteCommandArgs args = new ExecuteCommandArgs();
        args.command = command;
        args.cwd = cwd;
        return tool.execute(args, new ToolExecutionContext("run", "session", null, null));
    }

    /**
     * 只在当前 Unix 平台运行进程 fixture。
     */
    private void unixOnly() {
        assumeFalse(System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
    }

    /**
     * 只在允许枚举进程树的平台运行后代清理验证。
     */
    private void requireProcessEnumeration() {
        // 当前进程是否允许枚举子进程。
        boolean available;
        try {
            ProcessHandle.current().children().toList();
            available = true;
        } catch (RuntimeException unavailable) {
            available = false;
        }
        assumeTrue(available, "当前沙箱禁止枚举进程树");
    }
}
