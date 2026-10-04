package com.dingbang.myworld.aiframework.protocol;

import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import java.util.ArrayList;
import java.util.List;

/**
 * 记录模型事件和唯一终态。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class MultiProtocolGatewayTestRecorder implements ModelEventListener {
    /**
     * 收到的模型事件。
     */
    final List<ModelEvent> events = new ArrayList<>();
    /**
     * 错误终态。
     */
    Throwable error;
    /**
     * 正常终态次数。
     */
    int completed;

    /**
     * @param event 收到的业务事件
     */
    @Override public void onEvent(ModelEvent event) { events.add(event); }
    /**
     * @param error 模型错误
     */
    @Override public void onError(Throwable error) { this.error = error; }
    /**
     * 记录正常结束。
     */
    @Override public void onComplete() { completed++; }
    /**
     * @return 唯一完整回合
     */
    ModelTurn turn() { return ((TurnCompleted) events.get(events.size() - 1)).getTurn(); }
}
