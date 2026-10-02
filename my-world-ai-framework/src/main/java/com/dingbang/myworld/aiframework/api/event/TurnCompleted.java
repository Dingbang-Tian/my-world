package com.dingbang.myworld.aiframework.api.event;

import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import lombok.Data;

import java.util.Objects;

/**
 * 结束本次请求并携带完整助手回合。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Data
public final class TurnCompleted implements ModelEvent {

    /**
     * 完整助手回合。
     */
    private final ModelTurn turn;

    /**
     * 校验完整回合不为 null。
     *
     * @param turn 完整助手回合
     * @throws NullPointerException 当完整回合为 null 时
     */
    public TurnCompleted(ModelTurn turn) {
        this.turn = Objects.requireNonNull(turn, "完整回合不能为 null");
    }
}
