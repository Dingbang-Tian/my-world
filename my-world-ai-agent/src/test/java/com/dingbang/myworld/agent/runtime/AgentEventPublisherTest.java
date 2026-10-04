package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventException;
import com.dingbang.myworld.agent.api.AgentEventType;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证事件历史缺口和慢消费者的有限队列边界。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class AgentEventPublisherTest {
    /**
     * 验证已淘汰历史会明确报错，调用方可按可用序号续订。
     *
     * @throws Exception 等待异步回放失败时
     */
    @Test
    void reportsHistoryGapAndAllowsCursorResume() throws Exception {
        // 容量为两条的发布器。
        AgentEventPublisher publisher = new AgentEventPublisher(2);
        publisher.publish(event(1));
        publisher.publish(event(2));
        publisher.publish(event(3));
        assertThatThrownBy(() -> publisher.subscribe(new AgentEventPublisherTestSilentListener(), 0))
                .isInstanceOf(AgentEventException.class)
                .satisfies(error -> {
                    assertThat(((AgentEventException) error).getCode()).isEqualTo("EVENT_HISTORY_GAP");
                    assertThat(((AgentEventException) error).getFirstAvailableSequence()).isEqualTo(2);
                });
        // 从第一条之后恢复的观察者。
        RecordingAgentEventListener resumed = new RecordingAgentEventListener();
        publisher.subscribe(resumed, 1);
        publisher.complete();
        assertThat(resumed.awaitCompletion(2, TimeUnit.SECONDS)).isTrue();
        assertThat(resumed.getEvents()).extracting(AgentEvent::getSequence).containsExactly(2L, 3L);
    }

    /**
     * 验证消费者阻塞时发布不阻塞，溢出只终止该订阅。
     *
     * @throws Exception 等待异步观察者失败时
     */
    @Test
    void slowSubscriberGetsExplicitErrorWithoutBlockingPublisher() throws Exception {
        // 独立的消费者执行线程。
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 有界待消费队列。
            AgentEventPublisher publisher = new AgentEventPublisher(2, executor);
            // 首个事件已经进入用户回调的门闩。
            CountDownLatch entered = new CountDownLatch(1);
            // 释放阻塞消费者的门闩。
            CountDownLatch release = new CountDownLatch(1);
            // 消费者收到明确错误的门闩。
            CountDownLatch failed = new CountDownLatch(1);
            // 当前订阅的错误。
            AtomicReference<AgentEventException> error = new AtomicReference<>();
            publisher.subscribe(new SlowAgentEventListener(entered, release, failed, error), 0);
            publisher.publish(event(1));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            publisher.publish(event(2));
            publisher.publish(event(3));
            publisher.publish(event(4));
            release.countDown();
            assertThat(failed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(error.get().getCode()).isEqualTo("SLOW_CONSUMER");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 创建有效文本事件。
     *
     * @param sequence 顺序号
     * @return 事件
     */
    private static AgentEvent event(long sequence) {
        return new AgentEvent("run", "session", sequence, Instant.now(), AgentEventType.TEXT_DELTA, "x", null);
    }

}
