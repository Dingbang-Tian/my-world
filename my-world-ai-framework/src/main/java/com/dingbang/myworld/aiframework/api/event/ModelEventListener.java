package com.dingbang.myworld.aiframework.api.event;

/**
 * 接收一次模型调用产生的事件、错误和结束通知。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public interface ModelEventListener {

    /**
     * 接收模型产生的一条事件。
     *
     * @param event 模型事件
     */
    void onEvent(ModelEvent event);

    /**
     * 接收模型调用失败通知。
     *
     * @param error 调用失败原因
     */
    void onError(Throwable error);

    /**
     * 接收模型正常结束通知。
     */
    void onComplete();
}
