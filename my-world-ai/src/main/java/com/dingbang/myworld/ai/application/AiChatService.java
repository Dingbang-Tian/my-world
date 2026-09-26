package com.dingbang.myworld.ai.application;

import reactor.core.publisher.Flux;

/**
 * 项目内部的 AI 对话服务接口。
 * 业务模块应依赖此接口，避免直接依赖 Spring AI 的模型类型。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public interface AiChatService {

    /**
     * 发起一次同步对话。
     *
     * @param request 对话请求
     * @return 对话响应
     */
    AiChatResponse chat(AiChatRequest request);

    /**
     * 发起一次流式对话。
     *
     * @param request 对话请求
     * @return 响应内容流
     */
    Flux<String> stream(AiChatRequest request);
}
