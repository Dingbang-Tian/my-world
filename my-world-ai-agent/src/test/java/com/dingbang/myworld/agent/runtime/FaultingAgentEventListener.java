package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventException;
import com.dingbang.myworld.agent.api.AgentEventListener;

import java.util.concurrent.CountDownLatch;

/**
 * 在测试中模拟抛错的 Agent 事件观察者。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class FaultingAgentEventListener implements AgentEventListener {
    /**
     * 已收到观察者级错误的通知。
     */
    private final CountDownLatch failed;

    /**
     * 绑定错误通知。
     *
     * @param failed 已收到错误的门闩
     */
    FaultingAgentEventListener(CountDownLatch failed) {
        this.failed = failed;
    }

    /**
     * 故意拒绝事件。
     *
     * @param event 当前事件
     */
    @Override
    public void onEvent(AgentEvent event) {
        throw new IllegalStateException("观察者模拟故障");
    }

    /**
     * 接收正常结束通知。
     */
    @Override
    public void onComplete() { }

    /**
     * 通知测试已收到观察者错误。
     *
     * @param error 当前订阅错误
     */
    @Override
    public void onError(AgentEventException error) {
        failed.countDown();
    }
}
