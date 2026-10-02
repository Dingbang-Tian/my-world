package com.dingbang.myworld.agent.api;

import com.dingbang.myworld.agent.tool.ToolExecutionEvent;
import lombok.Data;

import java.time.Instant;
import java.util.Objects;

/**
 * 带运行关联、顺序号和内容的不可变 Agent 事件。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class AgentEvent {

    /**
     * 运行标识。
     */
    private final String runId;

    /**
     * 会话标识。
     */
    private final String sessionId;

    /**
     * 当前运行内从 1 开始的顺序号。
     */
    private final long sequence;

    /**
     * 事件发生时间。
     */
    private final Instant timestamp;

    /**
     * 事件种类。
     */
    private final AgentEventType type;

    /**
     * 文本增量；终态事件时为 null。
     */
    private final String text;

    /**
     * 最终结果；文本增量事件时为 null。
     */
    private final AgentResult result;

    /**
     * 工具执行阶段信息，仅工具事件时非 null。
     */
    private final ToolExecutionEvent toolExecution;

    /**
     * 创建一次运行事件。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param sequence 当前运行内顺序号
     * @param timestamp 事件时间
     * @param type 事件种类
     * @param text 文本增量或 null
     * @param result 最终结果或 null
     * @throws IllegalArgumentException 顺序号或事件内容与种类不匹配时
     */
    public AgentEvent(String runId, String sessionId, long sequence, Instant timestamp,
                      AgentEventType type, String text, AgentResult result) {
        this(runId, sessionId, sequence, timestamp, type, text, result, null);
    }

    /**
     * 创建包含可选工具阶段的运行事件。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param sequence 当前运行的顺序号
     * @param timestamp 事件时间
     * @param type 事件类型
     * @param text 文本增量
     * @param result 终态结果
     * @param toolExecution 工具阶段信息
     */
    public AgentEvent(String runId, String sessionId, long sequence, Instant timestamp,
                      AgentEventType type, String text, AgentResult result,
                      ToolExecutionEvent toolExecution) {
        this.runId = Objects.requireNonNull(runId, "运行标识不能为 null");
        this.sessionId = Objects.requireNonNull(sessionId, "会话标识不能为 null");
        if (sequence < 1) {
            throw new IllegalArgumentException("事件顺序号必须从 1 开始");
        }
        this.sequence = sequence;
        this.timestamp = Objects.requireNonNull(timestamp, "事件时间不能为 null");
        this.type = Objects.requireNonNull(type, "事件类型不能为 null");
        if (type == AgentEventType.TEXT_DELTA && (text == null || result != null || toolExecution != null)) {
            throw new IllegalArgumentException("文本事件必须只包含文本增量");
        }
        if (type == AgentEventType.TOOL_EXECUTION
                && (text != null || result != null || toolExecution == null)) {
            throw new IllegalArgumentException("工具事件必须只包含工具阶段");
        }
        if (type != AgentEventType.TEXT_DELTA && type != AgentEventType.TOOL_EXECUTION
                && (text != null || result == null || toolExecution != null)) {
            throw new IllegalArgumentException("终态事件必须只包含最终结果");
        }
        this.text = text;
        this.result = result;
        this.toolExecution = toolExecution;
    }
}
