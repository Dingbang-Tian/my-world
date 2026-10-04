package com.dingbang.myworld.ai.adapter;

import com.dingbang.myworld.ai.application.AiChatRequest;
import com.dingbang.myworld.ai.application.AiChatResponse;
import com.dingbang.myworld.ai.application.AiChatService;
import com.dingbang.myworld.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * 基于 Spring AI ChatClient 的对话适配器。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@RequiredArgsConstructor
public class SpringAiChatAdapter implements AiChatService {

    /**
     * Spring AI 对话客户端。
     */
    private final ChatClient chatClient;

    /**
     * 项目 AI 模块配置。
     */
    private final AiProperties properties;

    /**
     * 发起同步对话并返回模型生成的内容。
     *
     * @param request 对话请求
     * @return 对话响应
     */
    @Override
    public AiChatResponse chat(AiChatRequest request) {
        // TODO: 增加请求参数校验、输入长度限制和业务异常映射。
        // 模型生成的文本内容。
        String content = chatClient.prompt()
                .user(request.getMessage())
                .call()
                .content();
        // TODO: 从响应元数据中提取实际模型、Token 使用量和请求追踪信息。
        return new AiChatResponse(content, "");
    }

    /**
     * 发起流式对话并返回模型生成的内容流。
     *
     * @param request 对话请求
     * @return 响应内容流
     */
    @Override
    public Flux<String> stream(AiChatRequest request) {
        // TODO: 增加会话记忆、取消处理、错误映射和流式审计。
        return chatClient.prompt()
                .user(request.getMessage())
                .stream()
                .content();
    }
}
