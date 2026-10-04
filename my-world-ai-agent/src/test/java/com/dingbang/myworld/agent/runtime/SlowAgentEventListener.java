package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventException;
import com.dingbang.myworld.agent.api.AgentEventListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 在测试中阻塞事件消费以触发慢消费者保护。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class SlowAgentEventListener implements AgentEventListener {
    /**
     * 已进入事件回调的通知。
     */
    private final CountDownLatch entered;
    /**
     * 释放阻塞回调的通知。
     */
    private final CountDownLatch release;
    /**
     * 已收到订阅错误的通知。
     */
    private final CountDownLatch failed;
    /**
     * 捕获的订阅错误。
     */
    private final AtomicReference<AgentEventException> error;

    /**
     * 绑定测试同步器。
     *
     * @param entered 回调已进入
     * @param release 允许回调继续
     * @param failed 已收到错误
     * @param error 错误容器
     */
    SlowAgentEventListener(CountDownLatch entered, CountDownLatch release,
                           CountDownLatch failed, AtomicReference<AgentEventException> error) {
        this.entered = entered;
        this.release = release;
        this.failed = failed;
        this.error = error;
    }

    /**
     * 阻塞事件消费直到测试释放。
     *
     * @param event 当前事件
     */
    @Override
    public void onEvent(AgentEvent event) {
        entered.countDown();
        try {
            release.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 接收正常结束通知。
     */
    @Override
    public void onComplete() { }

    /**
     * 保存慢消费者错误。
     *
     * @param failure 订阅错误
     */
    @Override
    public void onError(AgentEventException failure) {
        error.set(failure);
        failed.countDown();
    }
}
