package com.dingbang.myworld.ai.application;

import lombok.Value;

/**
 * AI 对话响应。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Value
public class AiChatResponse {

    /**
     * 模型返回的文本内容。
     */
    String content;

    /**
     * 实际使用的模型标识。
     */
    String model;
}
