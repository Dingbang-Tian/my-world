package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;

/**
 * 忽略正常模型事件并拒绝意外错误。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class AssertNoErrorModelListener implements ModelEventListener {
    /**
     * 接收普通模型事件。
     *
     * @param event 模型事件
     */
    @Override
    public void onEvent(ModelEvent event) { }

    /**
     * 把意外错误报告给测试。
     *
     * @param error 模型错误
     */
    @Override
    public void onError(Throwable error) {
        throw new AssertionError(error);
    }

    /**
     * 接收正常结束通知。
     */
    @Override
    public void onComplete() { }
}
