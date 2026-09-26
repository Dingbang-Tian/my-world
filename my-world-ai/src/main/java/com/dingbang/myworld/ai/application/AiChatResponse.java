package com.dingbang.myworld.ai.application;

/**
 * AI 对话响应。
 *
 * @param content 模型返回内容
 * @param model 实际使用的模型
 * @author Sebastian
 * @since 2026/09/25
 */
public record AiChatResponse(String content, String model) {
}
