package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventListener;

/**
 * 不参与断言的订阅占位。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class AgentEventPublisherTestSilentListener implements AgentEventListener {
    /**
     * 忽略事件。
     *
     * @param event 当前事件
     */
    @Override
    public void onEvent(AgentEvent event) { }

    /**
     * 忽略结束通知。
     */
    @Override
    public void onComplete() { }
}
