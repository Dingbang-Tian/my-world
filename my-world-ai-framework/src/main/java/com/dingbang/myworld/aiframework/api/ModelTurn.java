package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.common.utils.collection.CollectionUtils;
import lombok.Data;

import java.util.Objects;

/**
 * 模型单次回合的完整结果，是持久化助手消息的依据。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Data
public final class ModelTurn {

    /**
     * 含完整工具调用的助手消息。
     */
    private final Message assistantMessage;

    /**
     * 标准化结束原因。
     */
    private final ModelFinishReason finishReason;

    /**
     * 校验完整结果只能包含助手消息及匹配的工具结束原因。
     *
     * @param assistantMessage 完整助手消息
     * @param finishReason 标准化结束原因
     * @throws IllegalArgumentException 当消息角色或工具结束原因不匹配时
     * @throws NullPointerException 当结果字段为 null 时
     */
    public ModelTurn(Message assistantMessage, ModelFinishReason finishReason) {
        this.assistantMessage = Objects.requireNonNull(assistantMessage, "助手消息不能为 null");
        this.finishReason = Objects.requireNonNull(finishReason, "结束原因不能为 null");
        if (assistantMessage.getRole() != Role.ASSISTANT) {
            throw new IllegalArgumentException("模型完整结果必须包含助手消息");
        }
        if (finishReason == ModelFinishReason.TOOL_CALLS
                && CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
            throw new IllegalArgumentException("工具结束原因需要至少一条完整工具调用");
        }
    }

}
