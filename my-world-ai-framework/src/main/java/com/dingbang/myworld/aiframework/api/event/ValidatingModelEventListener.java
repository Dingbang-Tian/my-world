package com.dingbang.myworld.aiframework.api.event;

import com.dingbang.myworld.aiframework.api.event.TurnCompleted;

import java.util.Objects;

/**
 * 校验模型回调只包含一个完整回合，并转发给下游监听器。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public final class ValidatingModelEventListener implements ModelEventListener {

    /**

     * 接收已校验模型事件的下游监听器。

     */
    private final ModelEventListener delegate;

    /**

     * 是否已经收到完整回合。

     */
    private boolean receivedCompletedTurn;

    /**

     * 是否已经发送错误或正常结束通知。

     */
    private boolean terminated;

    /**
     * 创建带模型回合校验的监听器。
     *
     * @param delegate 接收已校验事件的监听器
     */
    public ValidatingModelEventListener(ModelEventListener delegate) {
        this.delegate = Objects.requireNonNull(delegate, "模型事件监听器不能为 null");
    }

    /**
     * 校验完整回合的位置后转发模型事件。
     *
     * @param event 模型事件
     */
    @Override
    public synchronized void onEvent(ModelEvent event) {
        // 所有回调串行处理，确保“收到完整回合”和“结束”两个状态不会发生竞态。
        ModelEvent actualEvent = Objects.requireNonNull(event, "模型事件不能为 null");
        if (terminated) {
            // 网关在错误或结束后继续回调时直接忽略，终态保持唯一。
            return;
        }
        if (receivedCompletedTurn) {
            // TurnCompleted 是本轮最后一条业务事件，后面只能是 onComplete 或 onError。
            notifyError(new IllegalStateException("完整回合之后不能继续发出事件"));
            return;
        }
        if (actualEvent instanceof TurnCompleted) {
            // 先记住完整回合已经到达，再转发给 Agent。
            receivedCompletedTurn = true;
        }
        try {
            delegate.onEvent(actualEvent);
        } catch (RuntimeException exception) {
            // 下游处理不接受该事件时，转换为模型调用失败。
            notifyError(exception);
        }
    }

    /**
     * 将模型错误转发为本次调用的唯一错误终态。
     *
     * @param error 模型调用失败原因
     */
    @Override
    public synchronized void onError(Throwable error) {
        notifyError(Objects.requireNonNull(error, "模型错误不能为 null"));
    }

    /**

     * 仅在已经收到完整回合时转发正常结束通知。

     */
    @Override
    public synchronized void onComplete() {
        if (terminated) {
            return;
        }
        if (!receivedCompletedTurn) {
            // 模型不能只输出增量后就宣称成功；持久化必须依赖完整助手消息。
            notifyError(new IllegalStateException("模型事件缺少完整回合"));
            return;
        }
        // 只有看到完整回合后，才允许把正常结束传递给 Agent。
        terminated = true;
        delegate.onComplete();
    }

    /**
     * 以错误结束本次回调，后续回调直接忽略。
     *
     * @param error 要转发的错误
     */
    private void notifyError(Throwable error) {
        if (terminated) {
            return;
        }
        // 先写入终态标记，阻止错误回调与正常结束回调重复传递。
        terminated = true;
        delegate.onError(error);
    }
}
