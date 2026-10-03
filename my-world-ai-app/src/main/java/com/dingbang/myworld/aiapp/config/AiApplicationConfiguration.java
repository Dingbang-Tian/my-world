package com.dingbang.myworld.aiapp.config;

import com.dingbang.myworld.agent.config.AgentModuleConfiguration;
import com.dingbang.myworld.aiapp.config.model.OpenAiChatConfiguration;
import com.dingbang.myworld.aiapp.codegen.config.CodegenConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 装配 AI 应用模块及其公共 Agent 配置。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Configuration(proxyBeanMethods = false)
@Import({AgentModuleConfiguration.class, OpenAiChatConfiguration.class, CodegenConfiguration.class})
public class AiApplicationConfiguration {
}
