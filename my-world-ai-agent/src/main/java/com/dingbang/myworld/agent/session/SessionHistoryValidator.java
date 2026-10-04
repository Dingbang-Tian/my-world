package com.dingbang.myworld.agent.session;

import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 检查已完成交换及工具调用结果的顺序与配对。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SessionHistoryValidator {

    /**
     * 校验一段或多段连续的完整交换。
     *
     * @param messages 待提交或导入的消息
     */
    static void validateExchange(List<Message> messages) {
        Objects.requireNonNull(messages, "消息列表不能为 null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("交换不能为空");
        }
        // 尚未收到结果的调用标识。
        Set<String> pending = new HashSet<>();
        // 当前是否已经收到用户消息。
        boolean inExchange = false;
        // 是否正在等待工具结果。
        boolean waitingTools = false;
        // 当前待检查的消息。
        for (Message message : messages) {
            Objects.requireNonNull(message, "消息不能为 null");
            if (message.getRole() == Role.USER) {
                if (inExchange) {
                    throw new IllegalArgumentException("前一段交换尚未完成");
                }
                inExchange = true;
            } else if (message.getRole() == Role.ASSISTANT) {
                if (!inExchange || !pending.isEmpty()) {
                    throw new IllegalArgumentException("助手消息位置非法或工具结果缺失");
                }
                waitingTools = !message.getToolCalls().isEmpty();
                if (waitingTools) {
                    // 当前助手消息提出的工具调用。
                    for (ToolCall call : message.getToolCalls()) {
                        if (!pending.add(call.getCallId())) {
                            throw new IllegalArgumentException("同一批工具调用标识重复");
                        }
                    }
                } else {
                    inExchange = false;
                }
            } else if (message.getRole() == Role.TOOL) {
                if (!waitingTools || message.getToolResults().size() != 1) {
                    throw new IllegalArgumentException("工具结果位置或数量非法");
                }
                // 当前工具结果。
                ToolResult result = message.getToolResults().get(0);
                if (!pending.remove(result.getCallId())) {
                    throw new IllegalArgumentException("工具结果没有对应调用");
                }
                if (pending.isEmpty()) {
                    waitingTools = false;
                }
            } else {
                throw new IllegalArgumentException("会话历史不能包含 SYSTEM 消息");
            }
        }
        if (inExchange || !pending.isEmpty()) {
            throw new IllegalArgumentException("会话历史末尾是不完整交换");
        }
    }
}
