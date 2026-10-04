package com.dingbang.myworld.agent.orchestration;

import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.aiframework.api.CancellationToken;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * 顺序启动独立子运行，在完成回调中唤醒父运行并传播父取消。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class SubAgentRunner {
    /**
     * 当前委派对应的独立子运行。
     */
    private final AgentRun child;
    /**
     * 父运行的取消信号。
     */
    private final CancellationToken parentCancellation;

    /**
     * 固定子运行及父取消信号。
     *
     * @param child 独立子运行
     * @param parentCancellation 父运行取消信号
     */
    public SubAgentRunner(AgentRun child, CancellationToken parentCancellation) {
        this.child = Objects.requireNonNull(child, "子运行不能为 null");
        this.parentCancellation = Objects.requireNonNull(parentCancellation, "父取消信号不能为 null");
    }

    /**
     * 启动子运行并异步等待唯一终态，等待期间响应父取消。
     *
     * @param completion 子运行结束时接收真实结果或异常的回调
     */
    public void start(BiConsumer<AgentResult, Throwable> completion) {
        Objects.requireNonNull(completion, "完成回调不能为 null");
        // 启动阶段抛出的异常，优先于取消结果报告。
        AtomicReference<Throwable> startupError = new AtomicReference<>();
        // 子运行结束时注销的父取消回调。
        Runnable unregister = parentCancellation.onCancel(child::cancel);
        child.getResult().whenComplete((result, error) -> {
            unregister.run();
            completion.accept(result, startupError.get() == null ? error : startupError.get());
        });
        try {
            if (!parentCancellation.isCancelled()) {
                try {
                    child.execute();
                } catch (IllegalStateException exception) {
                    if (!parentCancellation.isCancelled()) {
                        throw exception;
                    }
                }
            }
        } catch (RuntimeException exception) {
            startupError.set(exception);
            child.cancel();
        }
    }
}
