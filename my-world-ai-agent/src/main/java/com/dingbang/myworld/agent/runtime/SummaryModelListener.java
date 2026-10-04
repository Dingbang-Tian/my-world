package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

/**
 * 收集摘要模型的完整回合并交付等待方。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class SummaryModelListener implements ModelEventListener {
    /**
     * 等待摘要结果的 Future。
     */
    private final CompletableFuture<ModelTurn> completed;
    /**
     * 已接收但尚未确认结束的完整回合。
     */
    private ModelTurn received;

    /**
     * 保存完整回合事件。
     *
     * @param event 模型事件
     */
    @Override
    public void onEvent(ModelEvent event) {
        if (event instanceof TurnCompleted turn)  {
            received = turn.getTurn();
        }
    }

    /**
     * 传播摘要失败。
     *
     * @param error 模型错误
     */
    @Override
    public void onError(Throwable error) {
        completed.completeExceptionally(error);
    }

    /**
     * 在流结束时交付唯一完整回合。
     */
    @Override
    public void onComplete() {
        if (received == null) completed.completeExceptionally(
                new IllegalStateException("摘要模型未返回完整回合"));
        else {
            completed.complete(received);
        }
    }
}
