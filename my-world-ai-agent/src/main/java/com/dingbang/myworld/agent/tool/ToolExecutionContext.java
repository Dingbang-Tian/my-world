package com.dingbang.myworld.agent.tool;

import lombok.Data;

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

    /**
     * 固定可信运行上下文。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param workspace 已授权工作目录，可为 null
     * @param deadline 截止时刻，可为 null
     */
    public ToolExecutionContext(String runId, String sessionId, Path workspace, Instant deadline) {
        this.runId = Objects.requireNonNull(runId, "运行标识不能为 null");
        this.sessionId = Objects.requireNonNull(sessionId, "会话标识不能为 null");
        this.workspace = workspace;
        this.deadline = deadline;
    }
}
