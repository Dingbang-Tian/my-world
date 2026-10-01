package com.dingbang.myworld;

import com.dingbang.myworld.agent.config.AgentModuleConfiguration;
import com.dingbang.myworld.ai.adapter.SpringAiChatAdapter;
import com.dingbang.myworld.ai.application.AiChatService;
import com.dingbang.myworld.ai.config.SpringAiConfiguration;
import com.dingbang.myworld.aiapp.config.AiApplicationConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证启动模块能够装配 AI 应用、Agent 和现有普通对话配置。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
class ModuleAssemblySmokeTest {

    /**
     * 验证模块配置链和普通对话服务均已装配，且无需调用远程模型。
     */
    @Test
    void loadsModuleChainAndExistingChatService() {
        /** 使用测试配置与假模型启动的应用上下文。 */
        ConfigurableApplicationContext context = new SpringApplicationBuilder(
                MyWorldApplication.class, FakeModelConfiguration.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties("spring.config.name=s01-smoke")
                .run();
        try (context) {
            assertThat(context.getBean(AiApplicationConfiguration.class)).isNotNull();
            assertThat(context.getBean(AgentModuleConfiguration.class)).isNotNull();
            assertThat(context.getBean(SpringAiConfiguration.class)).isNotNull();
            assertThat(context.getBean(AiChatService.class)).isInstanceOf(SpringAiChatAdapter.class);
        }
    }

    /**
     * 为装配测试提供不会请求网络的假模型客户端构建器。
     *
     * @author Sebastian
     * @since 2026/10/01
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeModelConfiguration {

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
}
