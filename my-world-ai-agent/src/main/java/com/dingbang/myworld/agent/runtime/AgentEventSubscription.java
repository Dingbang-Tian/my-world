package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventException;
import com.dingbang.myworld.agent.api.AgentEventListener;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

/**
 * 串行异步派发单个消费者的有限事件，隔离消费者阻塞和异常。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
final class AgentEventSubscription {
    /** 订阅监听器。 */
    private final AgentEventListener listener;
    /** 最大待处理事件数。 */
    private final int capacity;
    /** 消费者执行器。 */
    private final Executor executor;
    /** 等待派发的事件。 */
    private final Deque<AgentEvent> pending = new ArrayDeque<>();
    /** 是否已有派发任务。 */
    private boolean draining;
    /** 是否已提交结束通知。 */
    private boolean completed;
    /** 是否不再接受任何事件。 */
    private boolean closed;
    /** 终止当前订阅的错误。 */
    private AgentEventException failure;

    /**
     * 创建单消费者队列。
     *
     * @param listener 观察者
     * @param capacity 队列容量
     * @param executor 独立事件执行器
     */
    AgentEventSubscription(AgentEventListener listener, int capacity, Executor executor) {
        this.listener = listener;
        this.capacity = capacity;
        this.executor = executor;
    }

    /**
     * 非阻塞加入一条事件，容量耗尽只结束此订阅。
     *
     * @param event 待派发事件
     */
    synchronized void offer(AgentEvent event) {
        if (closed || completed) {
            return;
        }
        if (pending.size() == capacity) {
            failure = new AgentEventException("SLOW_CONSUMER", "事件消费过慢，请查询运行结果", event.getSequence());
            pending.clear();
            closed = true;
        } else {
            pending.addLast(event);
        }
    }

    /**
     * 标记所有已入队事件派发后结束。
     */
    synchronized void complete() {
        completed = true;
    }

    /**
     * 返回是否已脱离实时订阅。
     *
     * @return 已关闭时为 true
     */
    synchronized boolean isClosed() {
        return closed;
    }

    /**
     * 保证同一消费者仅有一个派发任务；线程池饱和时清空队列并关闭订阅。
     *
     * @throws AgentEventException 事件执行器无法接受任务时
     */
    synchronized void dispatch() {
        if (draining || (closed && failure == null)) {
            return;
        }
        draining = true;
        try {
            executor.execute(this::drain);
        } catch (RuntimeException exception) {
            draining = false;
            closed = true;
            pending.clear();
            throw new AgentEventException("EVENT_DISPATCH_REJECTED", "事件执行器已饱和，请查询运行结果", 0);
        }
    }

    /**
     * 在队列锁外调用消费者；消费者异常只关闭当前订阅。
     */
    private void drain() {
        while (true) {
            /** 本次待发送事件。 */
            AgentEvent event;
            /** 本次待报告错误。 */
            AgentEventException error;
            synchronized (this) {
                error = failure;
                failure = null;
                event = pending.pollFirst();
                if (error == null && event == null && !completed) {
                    draining = false;
                    return;
                }
                if (error != null || event == null) {
                    closed = true;
                }
            }
            try {
                if (error != null) {
                    listener.onError(error);
                    return;
                }
                if (event == null) {
                    listener.onComplete();
                    return;
                }
                listener.onEvent(event);
            } catch (RuntimeException exception) {
                synchronized (this) {
                    closed = true;
                    pending.clear();
                }
                try {
                    listener.onError(new AgentEventException("LISTENER_FAILURE",
                            "事件观察者处理失败: " + exception.getClass().getSimpleName(),
                            event == null ? 0 : event.getSequence()));
                } catch (RuntimeException ignored) {
                    // 观察者的错误处理再次失败也不能改变 Agent 运行。
                }
                return;
            }
        }
    }
}
