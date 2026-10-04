package com.dingbang.myworld.web.ai.codegen;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统计控制器测试收到的运行事件。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class CountingAgentEventListener implements AgentEventListener {
    /**
     * 已收到的事件数量。
     */
    private final AtomicInteger events;
    /**
     * 事件回放结束的通知。
     */
    private final CountDownLatch complete;

    /**
     * 绑定计数器和结束通知。
     *
     * @param events 事件计数器
     * @param complete 结束通知
     */
    CountingAgentEventListener(AtomicInteger events, CountDownLatch complete) {
        this.events = events;
        this.complete = complete;
    }

    /**
     * 计数运行事件。
     *
     * @param event 运行事件
     */
    @Override
    public void onEvent(AgentEvent event) {
        events.incrementAndGet();
    }

    /**
     * 通知回放完成。
     */
    @Override
    public void onComplete() {
        complete.countDown();
    }
}
