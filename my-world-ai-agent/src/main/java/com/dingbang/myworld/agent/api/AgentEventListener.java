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

    /**
     * 接收当前订阅的慢消费者或调度失败错误；运行结果仍可独立查询。
     *
     * @param error 当前订阅错误
     */
    default void onError(AgentEventException error) {
        onComplete();
    }
}
