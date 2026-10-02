package com.dingbang.myworld.aiframework.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 在线程和协议订阅间传播一次取消，并允许资源注销回调。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class CancellationToken {
    /** 等待取消的资源清理回调。 */
    private final List<Runnable> callbacks = new ArrayList<>();
    /** 是否已经请求取消。 */
    private volatile boolean cancelled;

    /**
     * 返回取消状态。
     *
     * @return 已取消时为 true
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * 在取消后拒绝继续工作。
     *
     * @throws ExecutionControlException 已取消时
     */
    public void checkCancelled() {
        if (cancelled) {
            throw new ExecutionControlException("CANCELLED", "运行已取消");
        }
    }

    /**
     * 注册非阻塞的资源清理动作；已取消时立即调用，返回值用于注销。
     *
     * @param callback 取消时执行的清理动作
     * @return 无需捕获受检异常的注销动作
     */
    public Runnable onCancel(Runnable callback) {
        Objects.requireNonNull(callback, "取消回调不能为 null");
        synchronized (this) {
            if (!cancelled) {
                callbacks.add(callback);
                return () -> remove(callback);
            }
        }
        notifyCancellation(callback);
        return () -> { };
    }

    /**
     * 幂等取消并在锁外清理所有已登记的资源。
     */
    public void cancel() {
        /** 本次唯一获得清理权的回调快照。 */
        List<Runnable> pending;
        synchronized (this) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            pending = new ArrayList<>(callbacks);
            callbacks.clear();
        }
        pending.forEach(CancellationToken::notifyCancellation);
    }

    /**
     * 移除正常结束的资源回调。
     *
     * @param callback 原注册动作
     */
    private synchronized void remove(Runnable callback) {
        callbacks.remove(callback);
    }

    /**
     * 隔离一个资源的清理失败。
     *
     * @param callback 资源清理动作
     */
    private static void notifyCancellation(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ignored) {
            // 一个资源清理失败不能阻止其他资源响应取消。
        }
    }
}
