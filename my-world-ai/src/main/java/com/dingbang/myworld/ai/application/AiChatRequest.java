package com.dingbang.myworld.ai.application;

/**
 * AI 对话请求。
 *
 * @param message 用户消息
 * @param conversationId 会话标识
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public record AiChatRequest(String message, String conversationId) {
}
