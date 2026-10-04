package com.dingbang.myworld;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 为装配测试提供不会请求网络的假模型客户端构建器。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@TestConfiguration(proxyBeanMethods = false)
class ModuleAssemblySmokeTestFakeModelConfiguration {

    /**
     * 创建由假模型支撑的 ChatClient 构建器。
     *
     * @return 不会自行发起网络请求的构建器
     */
    @Bean
    ChatClient.Builder testChatClientBuilder() {
        return ChatClient.builder(prompt -> {
            throw new AssertionError("S01 装配测试不应调用模型");
        });
    }
}
