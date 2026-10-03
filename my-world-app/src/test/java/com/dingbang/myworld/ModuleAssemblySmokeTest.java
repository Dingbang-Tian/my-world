package com.dingbang.myworld;

import com.dingbang.myworld.agent.config.AgentModuleConfiguration;
import com.dingbang.myworld.agent.persistence.RunJournal;
import com.dingbang.myworld.agent.persistence.mybatis.MybatisRunService;
import com.dingbang.myworld.agent.persistence.mybatis.MybatisSessionService;
import com.dingbang.myworld.agent.session.SessionRepository;
import com.dingbang.myworld.ai.adapter.SpringAiChatAdapter;
import com.dingbang.myworld.ai.application.AiChatService;
import com.dingbang.myworld.ai.config.SpringAiConfiguration;
import com.dingbang.myworld.aiapp.config.AiApplicationConfiguration;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.protocol.openai.OpenAiChatGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
     * 验证模块配置链、普通对话服务和显式启用的 Chat 网关均已装配，且无需调用远程模型。
     */
    @Test
    void loadsModuleChainAndExistingChatService() {
        // 使用测试配置与假模型启动的应用上下文。
        ConfigurableApplicationContext context = new SpringApplicationBuilder(
                MyWorldApplication.class, FakeModelConfiguration.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties("spring.config.name=s01-smoke",
                        "my-world.ai.chat.enabled=true",
                        "my-world.ai.chat.models.fixture.provider-id=local",
                        "my-world.ai.chat.models.fixture.endpoint=http://127.0.0.1:1/v1/chat/completions",
                        "my-world.ai.chat.models.fixture.model=wire-model",
                        "my-world.ai.chat.models.fixture.api-key=test-only-key")
                .run();
        try (context) {
            assertThat(context.getBean(AiApplicationConfiguration.class)).isNotNull();
            assertThat(context.getBean(AgentModuleConfiguration.class)).isNotNull();
            assertThat(context.getBean(SpringAiConfiguration.class)).isNotNull();
            assertThat(context.getBean(AiChatService.class)).isInstanceOf(SpringAiChatAdapter.class);
            assertThat(context.getBean(ModelGateway.class)).isInstanceOf(OpenAiChatGateway.class);
        }
    }

    /**
     * 验证启动模块在 MySQL 模式装配 MyBatis-Plus Service 和 Mapper。
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "MYWORLD_TEST_MYSQL_URL", matches = ".+")
    void loadsMysqlAgentStorageWithoutDefaultDatasource() {
        /** 使用专用 MySQL 测试库启动的应用上下文。 */
        ConfigurableApplicationContext context = new SpringApplicationBuilder(
                MyWorldApplication.class, FakeModelConfiguration.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties("spring.config.name=s01-smoke",
                        "my-world.agent.storage.type=mysql",
                        "my-world.agent.storage.url=" + System.getenv("MYWORLD_TEST_MYSQL_URL"),
                        "my-world.agent.storage.username="
                                + System.getenv().getOrDefault("MYWORLD_TEST_MYSQL_USER", "root"),
                        "my-world.agent.storage.password="
                                + System.getenv().getOrDefault("MYWORLD_TEST_MYSQL_PASSWORD", ""))
                .run();
        try (context) {
            assertThat(context.getBean(SessionRepository.class)).isInstanceOf(MybatisSessionService.class);
            assertThat(context.getBean(RunJournal.class)).isInstanceOf(MybatisRunService.class);
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
