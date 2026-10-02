package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 在进程内保存有限事件历史，并向 Agent 运行观察者发布事件。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
final class AgentEventPublisher {

    /**
     * 最多保留的历史事件数量。
     */
    private final int historyLimit;

    /**
     * 可供迟到观察者回放的事件历史。
     */
    private final List<AgentEvent> history = new ArrayList<>();

    /**
     * 正在等待后续事件的监听器。
     */
    private final List<AgentEventListener> listeners = new ArrayList<>();

    /**

     * 当前事件序列是否已经结束。

     */
    private boolean completed;

    /**
     * 创建事件发布器。
     *
     * @param historyLimit 最多保留的历史事件数量
     * @throws IllegalArgumentException 保留数量小于 1 时
     */
    AgentEventPublisher(int historyLimit) {
        if (historyLimit < 1) {
            throw new IllegalArgumentException("事件历史保留数量必须大于 0");
        }
        this.historyLimit = historyLimit;
    }

    /**
     * 回放已发生事件，并注册监听器接收后续事件。
     *
     * @param listener 接收 Agent 事件的监听器
     */
    synchronized void subscribe(AgentEventListener listener) {
        // 先校验监听器，再在同一把锁内完成回放和登记，避免两步之间漏掉新事件。
        AgentEventListener actualListener = Objects.requireNonNull(listener, "Agent 事件监听器不能为 null");
        for (AgentEvent event : history) {
            notifyEvent(actualListener, event);
        }
        if (completed) {
            // 迟到监听器只接收历史和结束通知，不会留在 listeners 中。
            notifyComplete(actualListener);
            return;
        }
        // 运行尚未结束，后续 publish 会把新事件发送给该监听器。
        listeners.add(actualListener);
    }

    /**
     * 记录一条新事件并发送给当前全部监听器。
     *
     * @param event 要发布的 Agent 事件
     * @throws IllegalStateException 事件序列已结束时
     */
    synchronized void publish(AgentEvent event) {
        // 运行结束后禁止补发事件，防止终态之后出现新的文本增量。
        AgentEvent actualEvent = Objects.requireNonNull(event, "Agent 事件不能为 null");
        if (completed) {
            throw new IllegalStateException("Agent 事件序列已经结束");
        }
        if (history.size() == historyLimit) {
            // 保持固定内存上限；最早的事件先被移除。
            history.remove(0);
        }
        history.add(actualEvent);
        // 复制监听器列表，避免监听器回调影响当前遍历。
        for (AgentEventListener listener : new ArrayList<>(listeners)) {
            notifyEvent(listener, actualEvent);
        }
    }

    /**

     * 结束事件序列并通知当前监听器。

     */
    synchronized void complete() {
        if (completed) {
            return;
        }
        // 先标记结束，保证回调期间新注册的监听器走“回放后结束”的分支。
        completed = true;
        for (AgentEventListener listener : new ArrayList<>(listeners)) {
            notifyComplete(listener);
        }
        // 有限运行结束后无需再保存活动监听器引用。
        listeners.clear();
    }

    /**
     * 隔离单个监听器的运行时错误，保证观察代码不会中断 Agent 运行。
     *
     * @param listener 事件监听器
     * @param event 要通知的事件
     */
    private void notifyEvent(AgentEventListener listener, AgentEvent event) {
        try {
            listener.onEvent(event);
        } catch (RuntimeException ignored) {
            // 事件观察者的异常不应改变模型调用和最终结果。
        }
    }

    /**
     * 隔离单个监听器的运行时错误，保证其他监听器仍能收到结束通知。
     *
     * @param listener 事件监听器
     */
    private void notifyComplete(AgentEventListener listener) {
        try {
            listener.onComplete();
        } catch (RuntimeException ignored) {
            // 事件观察者的异常不应阻止其他观察者结束。
        }
    }
}
