package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 在测试中记录模型事件和终态通知的监听器。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
final class RecordingModelEventListener implements ModelEventListener {

    /**
     * 已接收的模型事件。
     */
    private final List<ModelEvent> events = new ArrayList<>();

    /**
     * 模型调用错误；正常完成时为 null。
     */
    private Throwable error;

    /**
     * 是否已经收到正常结束通知。
     */
    private boolean completed;

    /**
     * 记录一条模型事件。
     *
     * @param event 模型事件
     */
    @Override
    public synchronized void onEvent(ModelEvent event) {
        events.add(event);
    }

    /**
     * 记录模型调用错误。
     *
     * @param error 模型调用错误
     */
    @Override
    public synchronized void onError(Throwable error) {
        this.error = error;
    }

    /**
     * 记录模型正常结束。
     */
    @Override
    public synchronized void onComplete() {
        completed = true;
    }

    /**
     * 获取已接收模型事件的副本。
     *
     * @return 模型事件副本
     */
    synchronized List<ModelEvent> getEvents() {
        return new ArrayList<>(events);
    }

    /**
     * 获取模型调用错误。
     *
     * @return 模型调用错误；正常完成时为 null
     */
    synchronized Throwable getError() {
        return error;
    }

    /**
     * 判断是否已经正常结束。
     *
     * @return 已收到正常结束通知时返回 true
     */
    synchronized boolean isCompleted() {
        return completed;
    }
}
