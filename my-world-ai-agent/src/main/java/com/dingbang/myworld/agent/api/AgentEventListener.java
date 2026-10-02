package com.dingbang.myworld.agent.api;

/**
 * 接收一次 Agent 运行的事件及其结束通知。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public interface AgentEventListener {

    /**
     * 接收 Agent 运行产生的一条事件。
     *
     * @param event Agent 事件
     */
    void onEvent(AgentEvent event);

    /**
     * 接收 Agent 事件序列结束通知。
     */
    void onComplete();
}
