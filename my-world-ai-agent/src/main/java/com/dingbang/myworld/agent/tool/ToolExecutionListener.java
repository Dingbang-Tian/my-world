package com.dingbang.myworld.agent.tool;

/**
 * 观察一次工具调用各阶段的监听器。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@FunctionalInterface
public interface ToolExecutionListener {
    /**
     * 接收工具调用的阶段事件。
     *
     * @param event 阶段事件
     */
    void onEvent(ToolExecutionEvent event);
}
