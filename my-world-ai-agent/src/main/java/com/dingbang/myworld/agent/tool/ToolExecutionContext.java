package com.dingbang.myworld.agent.tool;

import lombok.Data;
import com.dingbang.myworld.aiframework.api.CancellationToken;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * 由运行时提供且不从模型参数 JSON 读取的工具执行边界。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class ToolExecutionContext {
    /**
     * 运行标识。
     */
    private final String runId;
    /**
     * 会话标识。
     */
    private final String sessionId;
    /**
     * 已授权工作目录；无文件访问需要时可为 null。
     */
    private final Path workspace;
    /**
     * 调用截止时刻；未设置时可为 null。
     */
    private final Instant deadline;
    /** 由运行时共享的协作取消信号。 */
    private final CancellationToken cancellation;

    /**
     * 固定可信运行上下文。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param workspace 已授权工作目录，可为 null
     * @param deadline 截止时刻，可为 null
     */
    public ToolExecutionContext(String runId, String sessionId, Path workspace, Instant deadline) {
        this(runId, sessionId, workspace, deadline, new CancellationToken());
    }

    /**
     * 固定带协作取消的可信工具上下文。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param workspace 授权工作目录，可为空
     * @param deadline 全局截止时间，可为空
     * @param cancellation 运行取消令牌
     */
    public ToolExecutionContext(String runId, String sessionId, Path workspace, Instant deadline,
                                CancellationToken cancellation) {
        this.cancellation = Objects.requireNonNull(cancellation, "取消令牌不能为 null");
        this.runId = Objects.requireNonNull(runId, "运行标识不能为 null");
        this.sessionId = Objects.requireNonNull(sessionId, "会话标识不能为 null");
        this.workspace = workspace;
        this.deadline = deadline;
    }

    /**
     * 在工具执行或副作用之前检查取消和截止时间。
     *
     * @throws ExecutionControlException 已取消或超时时
     */
    public void checkActive() {
        cancellation.checkCancelled();
        if (deadline != null && !Instant.now().isBefore(deadline)) {
            throw new ExecutionControlException("TIMEOUT", "工具执行超过全局截止时间");
        }
    }
}
