package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventException;
import com.dingbang.myworld.agent.api.AgentEventListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 在进程内保存有限事件历史，并向 Agent 运行观察者发布事件。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
final class AgentEventPublisher {
    /**
     * 事件历史及每个消费者队列容量。
     */
    private final int historyLimit;
    /**
     * 事件派发专用执行器。
     */
    private final Executor executor;
    /**
     * 有界历史事件。
     */
    private final Deque<AgentEvent> history = new ArrayDeque<>();
    /**
     * 活跃订阅，单次运行最多 32 个。
     */
    private final List<AgentEventSubscription> listeners = new ArrayList<>();
    /**
     * 是否已关闭事件序列。
     */
    private boolean completed;
    /**
     * 最近发布的序号。
     */
    private long lastSequence;

    /**
     * 创建默认异步事件发布器。
     *
     * @param historyLimit 事件容量，必须大于零
     */
    AgentEventPublisher(int historyLimit) {
        this(historyLimit, AgentExecutors.EVENTS);
    }

    /**
     * 创建可注入事件执行器的发布器。
     *
     * @param historyLimit 事件容量
     * @param executor 非阻塞派发执行器
     */
    AgentEventPublisher(int historyLimit, Executor executor) {
        if (historyLimit < 1) {
            throw new IllegalArgumentException("事件历史保留数量必须大于零");
        }
        this.historyLimit = historyLimit;
        this.executor = Objects.requireNonNull(executor, "事件执行器不能为 null");
    }

    /**
     * 原子登记回放和实时事件，历史缺口明确拒绝。
     *
     * @param listener 事件观察者
     * @param afterSequence 已消费序号
     * @throws AgentEventException 历史缺失、订阅过多或执行器饱和时
     */
    synchronized void subscribe(AgentEventListener listener, long afterSequence) {
        Objects.requireNonNull(listener, "Agent 事件监听器不能为 null");
        // 当前有限历史的起点。
        long first = history.isEmpty() ? lastSequence + 1 : history.getFirst().getSequence();
        if (afterSequence < 0 || afterSequence > lastSequence) {
            throw new AgentEventException("INVALID_EVENT_CURSOR", "事件序号不在当前运行范围内", first);
        }
        if (afterSequence < first - 1) {
            throw new AgentEventException("EVENT_HISTORY_GAP", "事件历史已淘汰，请查询结果或从可用序号订阅", first);
        }
        listeners.removeIf(AgentEventSubscription::isClosed);
        if (listeners.size() >= 32) {
            throw new AgentEventException("TOO_MANY_SUBSCRIBERS", "单次运行最多允许 32 个活动订阅", first);
        }
        // 独立的消费者有界队列。
        AgentEventSubscription subscription = new AgentEventSubscription(listener, historyLimit, executor);
        // 当前可回放事件。
        for (AgentEvent event : history) {
            if (event.getSequence() > afterSequence) {
                subscription.offer(event);
            }
        }
        if (completed) {
            subscription.complete();
        } else {
            listeners.add(subscription);
        }
        subscription.dispatch();
    }

    /**
     * 保留有限历史并非阻塞投递给全部消费者。
     *
     * @param event 新事件
     */
    synchronized void publish(AgentEvent event) {
        if (completed) {
            return;
        }
        if (history.size() == historyLimit) {
            history.removeFirst();
        }
        history.addLast(event);
        lastSequence = event.getSequence();
        // 当前实时订阅。
        for (AgentEventSubscription subscription : listeners) {
            subscription.offer(event);
            dispatch(subscription);
        }
        listeners.removeIf(AgentEventSubscription::isClosed);
    }

    /**
     * 将结束通知排在已发布事件之后并释放实时订阅列表。
     */
    synchronized void complete() {
        if (completed) {
            return;
        }
        completed = true;
        // 当前需要结束的订阅。
        for (AgentEventSubscription subscription : listeners) {
            subscription.complete();
            dispatch(subscription);
        }
        listeners.clear();
    }

    /**
     * 让运行不受观察者线程池饱和影响，失败订阅由关闭标志移除。
     *
     * @param subscription 当前订阅
     */
    private void dispatch(AgentEventSubscription subscription) {
        try {
            subscription.dispatch();
        } catch (AgentEventException ignored) {
            // 调度失败不会改变 Agent 的业务结果；新订阅仍会直接收到明确错误。
        }
    }
}
