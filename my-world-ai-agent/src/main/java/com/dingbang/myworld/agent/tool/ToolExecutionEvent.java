package com.dingbang.myworld.agent.tool;

import lombok.Data;

import java.util.Objects;

/**
 * 与一个模型工具调用标识关联的执行阶段事件。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class ToolExecutionEvent {
    /**
     * 模型工具调用标识。
     */
    private final String callId;
    /**
     * 工具名称。
     */
    private final String toolName;
    /**
     * 执行阶段。
     */
    private final ToolExecutionPhase phase;
    /**
     * 本阶段已知的结果；准备和调用时为 null。
     */
    private final com.dingbang.myworld.aiframework.model.ToolResult result;

    /**
     * 固定事件关联信息。
     *
     * @param callId 工具调用标识
     * @param toolName 工具名称
     * @param phase 执行阶段
     * @param result 已知结果，可为 null
     */
    public ToolExecutionEvent(String callId, String toolName, ToolExecutionPhase phase,
                              com.dingbang.myworld.aiframework.model.ToolResult result) {
        this.callId = Objects.requireNonNull(callId, "调用标识不能为 null");
        this.toolName = Objects.requireNonNull(toolName, "工具名称不能为 null");
        this.phase = Objects.requireNonNull(phase, "阶段不能为 null");
        this.result = result;
    }
}
