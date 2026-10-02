package com.dingbang.myworld.agent.config;

import com.dingbang.myworld.agent.prompt.PromptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证项目 YAML 与启动阶段的提示词装配。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
class AgentPromptConfigurationTest {

    /**
     * 验证实际 YAML 绑定覆盖 Agent 模板，不使用普通聊天旧配置。
     */
    @Test
    void bindsYamlAndKeepsLegacyChatPromptSeparate() {
        // 只启动公共 Agent 配置，不连接任何真实模型。
        ConfigurableApplicationContext context = new SpringApplicationBuilder(
                AgentModuleConfiguration.class, TestPromptTemplateContributor.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.name=s03-prompt", "my-world.ai.default-system-prompt=普通聊天提示词")
                .run();
        try (context) {
            PromptRepository repository = context.getBean(PromptRepository.class);
            assertThat(repository.get("agent/system").render(Collections.singletonMap("agentName", "甲")))
                    .isEqualTo("项目 YAML 系统模板：甲\n");
            assertThat(repository.get("agent/summary").getContent()).isEqualTo("应用摘要：{{conversationText}}\n");
            assertThat(repository.get("agent/plan-step")
                    .render(Collections.singletonMap("stepNumber", "1"))).isEqualTo("项目计划步骤：1");
            assertThat(repository.get("agent/sub-agent")
                    .render(Collections.singletonMap("delegatedTask", "检查"))).isEqualTo("项目子任务：检查\n");
            assertThat(context.getEnvironment().getProperty("my-world.ai.default-system-prompt"))
                    .isEqualTo("普通聊天提示词");
        }
    }

    /**
     * 验证未配置项目覆盖时，应用资源覆盖公共默认模板。
     */
    @Test
    void loadsApplicationResourceWithoutProjectOverride() {
        // 显式注册应用模板提供者。
        new ApplicationContextRunner()
                .withUserConfiguration(AgentModuleConfiguration.class, TestPromptTemplateContributor.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PromptRepository.class).get("agent/system")
                            .render(Collections.singletonMap("agentName", "甲")))
                            .isEqualTo("应用系统模板：甲\n");
                });
    }

    /**
     * 验证文件加载错误使 Spring 上下文启动失败。
     */
    @Test
    void failsContextStartupForMissingTemplateFile() {
        // 使用不存在的模板位置，失败发生在 Bean 创建期间。
        new ApplicationContextRunner()
                .withUserConfiguration(AgentModuleConfiguration.class)
                .withPropertyValues("my-world.agent.prompts.system.location=file:/missing-s03-startup.md")
                .run(context -> assertThat(context).hasFailed());
    }
}
