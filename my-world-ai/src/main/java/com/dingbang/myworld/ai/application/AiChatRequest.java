package com.dingbang.myworld.ai.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 对话请求。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    /**
     * 用户输入的消息内容。
     */
    private String message;

    /**
     * 调用方传入的会话标识。
     */
    private String conversationId;
}
