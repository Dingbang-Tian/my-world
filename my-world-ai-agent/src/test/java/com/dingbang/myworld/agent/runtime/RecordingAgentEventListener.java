package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 在测试中记录 Agent 事件并等待事件序列结束的监听器。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
final class RecordingAgentEventListener implements AgentEventListener {

    /**
     * 已收到的 Agent 事件。
     */
    private final List<AgentEvent> events = new ArrayList<>();

    /**
     * 标记事件序列结束的同步门闩。
     */
    private final CountDownLatch completion = new CountDownLatch(1);

    /**
     * 记录一条 Agent 事件。
     *
     * @param event Agent 事件
     */
    @Override
    public synchronized void onEvent(AgentEvent event) {
        events.add(event);
    }

    /**
     * 标记 Agent 事件序列已经结束。
     */
    @Override
    public void onComplete() {
        completion.countDown();
    }

    /**
     * 等待当前事件序列结束。
     *
     * @param timeout 最长等待时间
     * @param unit 等待时间单位
     * @return 在超时前结束时返回 true
     * @throws InterruptedException 当前线程被中断时
     */
    boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return completion.await(timeout, unit);
    }

    /**
     * 获取已记录事件的副本。
     *
     * @return Agent 事件副本
     */
    synchronized List<AgentEvent> getEvents() {
        return new ArrayList<>(events);
    }
}
