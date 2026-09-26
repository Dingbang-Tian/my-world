package com.dingbang.myworld.ai.config;

import com.dingbang.myworld.ai.adapter.SpringAiChatAdapter;
import com.dingbang.myworld.ai.application.AiChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 基础配置。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "my-world.ai", name = "enabled", havingValue = "true")
public class SpringAiConfiguration {

    /**
     * 构造项目使用的 ChatClient。
     *
     * @param builder Spring AI 自动配置的构建器
     * @param properties AI 配置
     * @return ChatClient
     */
    @Bean
    public ChatClient myWorldChatClient(ChatClient.Builder builder, AiProperties properties) {
        // TODO: 补充正式的系统提示词和默认请求选项。
        // 配置后的 ChatClient 构建器。
        ChatClient.Builder configuredBuilder = builder;
        if (properties.defaultSystemPrompt() != null
                && !properties.defaultSystemPrompt().isBlank()) {
            configuredBuilder = configuredBuilder.defaultSystem(properties.defaultSystemPrompt());
        }
        return configuredBuilder.build();
    }

    /**
     * 注册项目内部的 AI 服务。
     *
     * @param chatClient Spring AI ChatClient
     * @param properties AI 配置
     * @return AI 服务
     */
    @Bean
    public AiChatService aiChatService(ChatClient chatClient, AiProperties properties) {
        return new SpringAiChatAdapter(chatClient, properties);
    }
}
