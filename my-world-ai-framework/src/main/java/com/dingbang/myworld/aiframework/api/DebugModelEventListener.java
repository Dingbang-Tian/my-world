package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 记录脱敏模型流元数据并转发原始事件。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class DebugModelEventListener implements ModelEventListener {
    /**
     * 与模型请求共用的调用标识。
     */
    private final String callId;
    /**
     * 原始事件监听器。
     */
    private final ModelEventListener delegate;
    /**
     * 调试日志入口。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeModelDebugGateway.class);

    /**
     * 记录流事件种类并转发。
     *
     * @param event 流事件
     */
    @Override
    public void onEvent(ModelEvent event) {
        LOGGER.debug("model_debug event=stream callId={} type={} content=[REDACTED]",
                callId, event.getClass().getSimpleName());
        delegate.onEvent(event);
    }

    /**
     * 记录错误种类并转发。
     *
     * @param error 模型错误
     */
    @Override
    public void onError(Throwable error) {
        LOGGER.debug("model_debug event=error callId={} errorType={}",
                callId, error.getClass().getSimpleName());
        delegate.onError(error);
    }

    /**
     * 记录模型流结束并转发。
     */
    @Override
    public void onComplete() {
        LOGGER.debug("model_debug event=complete callId={}", callId);
        delegate.onComplete();
    }
}
