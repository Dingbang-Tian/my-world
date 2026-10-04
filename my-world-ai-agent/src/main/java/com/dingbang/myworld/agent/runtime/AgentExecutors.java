package com.dingbang.myworld.agent.runtime;

import java.util.concurrent.Executor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 为阻塞工作、事件观察和截止时间提供互不占用的有界执行资源。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
final class AgentExecutors {
    /**
     * 模型和工具阻塞工作池，饱和时明确拒绝新任务。
     */
    static final Executor WORK = pool("agent-work-", 16);
    /**
     * 事件消费者独立工作池，慢观察者不占用模型线程。
     */
    static final Executor EVENTS = pool("agent-events-", 32);
    /**
     * 截止时间调度器，不在其线程执行工具或用户事件。
     */
    static final ScheduledThreadPoolExecutor TIMER = timer();

    /**
     * 创建不积压无限任务的守护线程池。
     *
     * @param prefix 线程名称前缀
     * @param maximum 最大线程数
     * @return 有界执行器
     */
    private static Executor pool(String prefix, int maximum) {
        // 有限线程和有限任务队列的执行器。
        ThreadPoolExecutor pool = new ThreadPoolExecutor(maximum, maximum, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(maximum * 16), factory(prefix),
                new ThreadPoolExecutor.AbortPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /**
     * 创建取消定时任务后立即清理队列的调度器。
     *
     * @return 截止时间调度器
     */
    private static ScheduledThreadPoolExecutor timer() {
        // 专用于轻量截止时间判定的调度器。
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2, factory("agent-deadline-"));
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /**
     * 创建命名守护线程工厂。
     *
     * @param prefix 名称前缀
     * @return 线程工厂
     */
    private static ThreadFactory factory(String prefix) {
        // 工厂内单调递增的线程编号。
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            // 当前新建线程。
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
