package com.dingbang.myworld.aiframework.protocol.openai;

import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import java.util.ArrayList;
import java.util.List;

/**
 * 记录单次模型调用中的事件与终态。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class OpenAiChatGatewayTestRecorder implements ModelEventListener {
    /**
     * 按顺序收到的模型事件。
     */
    final List<ModelEvent> events = new ArrayList<>();
    /**
     * 模型失败原因。
     */
    Throwable error;
    /**
     * 正常完成次数。
     */
    int completed;

    /**
     * 保存模型事件。
     *
     * @param event 当前模型事件
     */
    @Override
    public void onEvent(ModelEvent event) {
        events.add(event);
    }

    /**
     * 保存模型错误。
     *
     * @param error 失败原因
     */
    @Override
    public void onError(Throwable error) {
        this.error = error;
    }

    /**
     * 记录正常结束通知。
     */
    @Override
    public void onComplete() {
        completed++;
    }

    /**
     * 读取唯一完整模型回合。
     *
     * @return 完整回合
     */
    ModelTurn turn() {
        return ((TurnCompleted) events.get(events.size() - 1)).getTurn();
    }
}
