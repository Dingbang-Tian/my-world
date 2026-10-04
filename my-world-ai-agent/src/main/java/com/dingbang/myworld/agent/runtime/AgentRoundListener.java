package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;
import com.dingbang.myworld.aiframework.api.ModelGatewayException;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.ReasoningDelta;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.api.event.UsageReported;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * 单轮模型事件监听器。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class AgentRoundListener implements ModelEventListener {
    /**
     * 原运行对象，供回调访问当前状态。
     */
    private final DefaultAgentRun outer;

    /**
     * 当前回合编号。
     */
    private final int turnNumber;
    /**
     * 当前模型调用开始的单调时间。
     */
    private final long startedNanos;
    /**
     * 本回合最终完整结果。
     */
    private ModelTurn turn;
    /**
     * 本回合已经发布的模型字符数。
     */
    private long streamedCharacters;

    /**
     * 接纳一次模型增量；完成回合在 onComplete 后才处理。
     *
     * @param event 模型事件
     */
    @Override
    public void onEvent(ModelEvent event) {
        synchronized (outer.stateLock) {
            if (outer.terminal) {
                return;
            }
            if (outer.deadlineReached()) {
                outer.timeout();
                return;
            }
            if (event instanceof TextDelta || event instanceof ReasoningDelta) {
                // 当前增量的文本。
                String text = event instanceof TextDelta ? ((TextDelta) event).getText()
                        : ((ReasoningDelta) event).getText();
                if (outer.outputCharacters + streamedCharacters + text.length()
                        > outer.definition.getLimits().getMaxOutputCharacters()) {
                    outer.limitExceeded("模型输出达到字符上限", null);
                    return;
                }
                streamedCharacters += text.length();
                outer.emitLocked(event instanceof TextDelta ? AgentEventType.TEXT_DELTA
                        : AgentEventType.REASONING_DELTA, text, null, null, null);
            } else if (event instanceof TurnCompleted) {
                turn = ((TurnCompleted) event).getTurn();
            } else if (!(event instanceof UsageReported)) {
                throw new IllegalArgumentException("当前 Agent 阶段不支持该模型事件");
            }
        }
    }

    /**
     * 模型调用失败时尝试取得运行的失败终态。
     *
     * @param error 模型错误
     */
    @Override
    public void onError(Throwable error) {
        outer.audit.modelFinished(outer.runId + ":model:" + turnNumber, System.nanoTime() - startedNanos,
                error instanceof ModelGatewayException ? ((ModelGatewayException) error).getCode()
                        : error instanceof ExecutionControlException
                        ? ((ExecutionControlException) error).getCode() : "MODEL_FAILURE");
        if (error instanceof ExecutionControlException) {
            outer.stopForControl((ExecutionControlException) error);
        } else if (error instanceof ModelGatewayException) {
            outer.handleModelFailure(((ModelGatewayException) error).getCode(), error, turnNumber);
        } else {
            outer.handleModelFailure("MODEL_FAILURE", error, turnNumber);
        }
    }

    /**
     * 在模型回调线程外处理工具或最终提交。
     */
    @Override
    public void onComplete() {
        outer.audit.modelFinished(outer.runId + ":model:" + turnNumber, System.nanoTime() - startedNanos, "completed");
        try {
            outer.executor.execute(() -> outer.completeRound(turn, turnNumber, streamedCharacters));
        } catch (RuntimeException exception) {
            outer.handleModelFailure("PREPARATION_FAILURE", exception, turnNumber);
        }
    }
}
