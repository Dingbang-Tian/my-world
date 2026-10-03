package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolDescriptor;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import com.dingbang.myworld.aiapp.codegen.api.CommandReport;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 在显式授权的工作目录中运行受限时长的命令并回收进程树。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class ExecuteCommandTool implements Tool<ExecuteCommandTool.Args> {
    /** 输出最多保留的字节数。 */
    private static final int MAX_OUTPUT_BYTES = 16_000;
    /** 命令最长运行时间。 */
    private static final Duration MAX_DURATION = Duration.ofSeconds(60);
    /** 工作目录路径策略。 */
    private final WorkspacePolicy policy;
    /** 可继承的环境变量名称。 */
    private final List<String> environmentAllowlist;
    /** 本实例允许的最长执行时间。 */
    private final Duration maxDuration;
    /** 按运行标识保存命令执行报告。 */
    private final Map<String, List<CommandReport>> reports = new ConcurrentHashMap<>();

    /**
     * 绑定可信工作目录和环境变量白名单。
     *
     * @param policy 工作目录策略
     * @param environmentAllowlist 允许传给子进程的变量名称
     */
    public ExecuteCommandTool(WorkspacePolicy policy, List<String> environmentAllowlist) {
        this(policy, environmentAllowlist, MAX_DURATION);
    }

    /**
     * 为受控测试指定更短的命令超时。
     *
     * @param policy 工作目录策略
     * @param environmentAllowlist 环境变量白名单
     * @param maxDuration 命令时限，不得超过生产上限
     */
    ExecuteCommandTool(WorkspacePolicy policy, List<String> environmentAllowlist, Duration maxDuration) {
        this.policy = Objects.requireNonNull(policy, "工作目录策略不能为空");
        this.environmentAllowlist = List.copyOf(Objects.requireNonNull(environmentAllowlist, "环境白名单不能为空"));
        this.maxDuration = Objects.requireNonNull(maxDuration, "命令时限不能为空");
        if (maxDuration.isZero() || maxDuration.isNegative() || maxDuration.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("命令时限必须大于零且不超过 60 秒");
        }
        /** 当前校验的环境变量名称。 */
        for (String name : this.environmentAllowlist) {
            if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("无效的环境变量名称: " + name);
            }
        }
    }

    /**
     * 返回命令参数类型。
     *
     * @return 参数类
     */
    @Override
    public Class<Args> parameterType() {
        return Args.class;
    }

    /**
     * 返回命令工具描述。
     *
     * @return 工具描述
     */
    @Override
    public ToolDescriptor<Args> descriptor() {
        return ToolDescriptor.of("execute_command", "在工作目录中执行命令，返回退出码与合并输出", Args.class);
    }

    /**
     * 查询一次运行已完成的命令报告。
     *
     * @param runId Agent 运行标识
     * @return 不可修改的报告快照
     */
    public List<CommandReport> reports(String runId) {
        /** 本次运行的报告列表。 */
        List<CommandReport> found = reports.get(runId);
        if (found == null) {
            return List.of();
        }
        synchronized (found) {
            return List.copyOf(found);
        }
    }

    /**
     * 启动命令，同时读取有界输出并监测超时或取消。
     *
     * @param args 模型提交的命令参数
     * @param context 可信运行上下文
     * @return 包含退出码和输出的工具结果
     * @throws ExecutionControlException 父运行取消或全局超时时
     */
    @Override
    public ToolExecutionResult execute(Args args, ToolExecutionContext context) {
        Objects.requireNonNull(args, "命令参数不能为空");
        context.checkActive();
        if (args.command == null || args.command.isBlank()) {
            throw new IllegalArgumentException("command 不能为空");
        }
        /** 命令工作目录。 */
        Path cwd = policy.resolve(args.cwd == null ? "" : args.cwd);
        if (!Files.isDirectory(cwd)) {
            throw new IllegalArgumentException("cwd 不是已存在的目录");
        }
        /** 选择当前平台可用的 shell。 */
        List<String> invocation = shellCommand(args.shell, args.command);
        /** 启动前就确定的命令截止时间。 */
        Instant deadline = Instant.now().plus(maxDuration);
        /** 子进程构建器。 */
        ProcessBuilder builder = new ProcessBuilder(invocation).directory(cwd.toFile()).redirectErrorStream(true);
        /** 当前进程允许继承的环境变量快照。 */
        Map<String, String> parentEnvironment = System.getenv();
        builder.environment().clear();
        /** 当前允许继承的环境变量名称。 */
        for (String name : environmentAllowlist) {
            if (parentEnvironment.containsKey(name)) {
                builder.environment().put(name, parentEnvironment.get(name));
            }
        }
        /** 已启动的 shell 进程。 */
        Process process;
        try {
            context.checkActive();
            process = builder.start();
        } catch (IOException exception) {
            throw new IllegalArgumentException("命令启动失败: " + exception.getMessage(), exception);
        }
        try {
            process.getOutputStream().close();
        } catch (IOException exception) {
            destroyTree(process);
            throw new IllegalArgumentException("命令输入流关闭失败: " + exception.getMessage(), exception);
        }
        /** 并发读取的有界输出。 */
        OutputCapture capture = new OutputCapture(process.getInputStream());
        /** 不阻塞调用方终止的读取线程。 */
        Thread reader = new Thread(capture, "codegen-command-output");
        reader.setDaemon(true);
        reader.start();
        /** 父运行取消时立即清理进程树。 */
        Runnable unregister = context.getCancellation().onCancel(() -> destroyTree(process));
        try {
            while (true) {
                context.checkActive();
                if (!Instant.now().isBefore(deadline)) {
                    destroyTree(process);
                    record(context, args.command, cwd, invocation.get(0), "TIMED_OUT", null, capture);
                    return new ToolExecutionResult("timedOut=true\nexitCode=unavailable\n" + capture.text(), capture.truncated());
                }
                if (process.waitFor(50, TimeUnit.MILLISECONDS)) {
                    context.checkActive();
                    reader.join(1000);
                    /** 完成时退出码。 */
                    int exitCode = process.exitValue();
                    record(context, args.command, cwd, invocation.get(0), "COMPLETED", exitCode, capture);
                    return new ToolExecutionResult("timedOut=false\nexitCode=" + exitCode + "\n" + capture.text(),
                            capture.truncated());
                }
            }
        } catch (InterruptedException exception) {
            destroyTree(process);
            record(context, args.command, cwd, invocation.get(0), "CANCELLED", null, capture);
            Thread.currentThread().interrupt();
            throw new ExecutionControlException("CANCELLED", "命令执行线程被中断");
        } catch (ExecutionControlException exception) {
            destroyTree(process);
            record(context, args.command, cwd, invocation.get(0),
                    "TIMEOUT".equals(exception.getCode()) ? "TIMED_OUT" : "CANCELLED", null, capture);
            throw exception;
        } finally {
            unregister.run();
            if (process.isAlive()) {
                destroyTree(process);
            }
            try {
                process.getInputStream().close();
            } catch (IOException ignored) {
                // 进程退出时关闭流失败不改变已经确定的命令结果。
            }
        }
    }

    /**
     * 保存一条命令完成、超时或取消报告。
     *
     * @param context 运行上下文
     * @param command 命令文本
     * @param cwd 执行目录
     * @param shell 实际 shell
     * @param status 执行状态
     * @param exitCode 退出码，可为 null
     * @param capture 输出快照
     */
    private void record(ToolExecutionContext context, String command, Path cwd, String shell, String status,
                        Integer exitCode, OutputCapture capture) {
        /** 对外报告使用的相对目录。 */
        String relative = policy.root().relativize(cwd).toString();
        reports.computeIfAbsent(context.getRunId(), ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(new CommandReport(context.getRunId(), command, relative, shell, status, exitCode,
                        capture.text(), capture.truncated()));
    }

    /**
     * 在当前平台选择 shell，拒绝不支持的显式选择。
     *
     * @param shell auto、bash 或 powershell
     * @param command 命令文本
     * @return 进程参数列表
     */
    static List<String> shellCommand(String shell, String command) {
        /** 当前平台名称。 */
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        return shellCommand(shell, command, windows);
    }

    /**
     * 根据明确的平台标志选择 shell，便于不启动其他平台进程的契约测试。
     *
     * @param shell 请求的 shell
     * @param command 命令文本
     * @param windows 是否为 Windows 平台
     * @return 进程参数列表
     */
    static List<String> shellCommand(String shell, String command, boolean windows) {
        /** 调用者选择的 shell。 */
        String selected = shell == null || shell.isBlank() || shell.equalsIgnoreCase("auto")
                ? (windows ? "powershell" : "bash") : shell.toLowerCase(Locale.ROOT);
        if (selected.equals("bash") && !windows) {
            return List.of("/bin/bash", "-c", command);
        }
        if (selected.equals("powershell") && windows) {
            return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command);
        }
        throw new IllegalArgumentException("当前平台不支持 shell: " + selected);
    }

    /**
     * 先终止子孙进程，再终止 shell，防止父进程退出后遗留子进程。
     *
     * @param process shell 进程
     */
    private static void destroyTree(Process process) {
        /** 清理前捕获的子孙进程。 */
        List<ProcessHandle> descendants;
        try {
            descendants = new ArrayList<>(process.descendants().toList());
        } catch (RuntimeException unavailable) {
            // 某些受限系统禁止枚举进程；仍需保证直接启动的进程被终止。
            descendants = List.of();
        }
        /** 从最深处开始清理的后代索引。 */
        for (int index = descendants.size() - 1; index >= 0; index--) {
            descendants.get(index).destroyForcibly();
        }
        process.destroyForcibly();
    }

    /**
     * 模型可填写的命令参数。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public static final class Args {
        /** 要执行的命令文本。 */
        @ToolParam(description = "要执行的命令", required = true)
        public String command;
        /** 相对于工作目录的执行目录。 */
        @ToolParam(description = "执行目录相对路径，默认工作目录", required = false)
        public String cwd;
        /** auto、bash 或 powershell。 */
        @ToolParam(description = "shell：auto、bash 或 powershell，默认 auto", required = false)
        public String shell;
    }

    /**
     * 持续消费进程输出并只保留固定前缀。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    private static final class OutputCapture implements Runnable {
        /** 合并后的标准输出流。 */
        private final InputStream stream;
        /** 已保留的输出字节。 */
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(MAX_OUTPUT_BYTES);
        /** 是否发生截断。 */
        private boolean truncated;

        /**
         * 固定需消费的流。
         *
         * @param stream 进程输出流
         */
        private OutputCapture(InputStream stream) {
            this.stream = stream;
        }

        /** 持续读取直到进程关闭输出流。 */
        @Override
        public void run() {
            /** 每次读取的缓冲区。 */
            byte[] buffer = new byte[4096];
            try (stream) {
                /** 当前读取字节数。 */
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    synchronized (this) {
                        /** 本次仍可保留的字节数。 */
                        int retain = Math.min(count, MAX_OUTPUT_BYTES - bytes.size());
                        bytes.write(buffer, 0, retain);
                        truncated |= retain < count;
                    }
                }
            } catch (IOException ignored) {
                // 取消或超时关闭输出流时，已读取的前缀仍可返回。
            }
        }

        /**
         * 获取当前输出快照。
         *
         * @return UTF-8 文本
         */
        private synchronized String text() {
            return bytes.toString(StandardCharsets.UTF_8);
        }

        /**
         * 返回截断标志。
         *
         * @return 输出超限时为 true
         */
        private synchronized boolean truncated() {
            return truncated;
        }
    }
}
