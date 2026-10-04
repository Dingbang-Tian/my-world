package com.dingbang.myworld.aiframework.protocol.openai;

import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 保存模型取消测试收到的首个错误。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class ErrorRecordingModelListener implements ModelEventListener {
    /**
     * 首个模型错误的存储位置。
     */
    private final AtomicReference<Throwable> error;

    /**
     * 绑定错误容器。
     *
     * @param error 错误容器
     */
    ErrorRecordingModelListener(AtomicReference<Throwable> error) {
        this.error = error;
    }

    /**
     * 忽略模型增量。
     *
     * @param event 当前模型事件
     */
    @Override
    public void onEvent(ModelEvent event) { }

    /**
     * 保存首个错误。
     *
     * @param failure 网关错误
     */
    @Override
    public void onError(Throwable failure) {
        error.compareAndSet(null, failure);
    }

    /**
     * 接收正常结束通知。
     */
    @Override
    public void onComplete() { }
}
