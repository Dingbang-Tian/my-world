package com.dingbang.myworld.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 装配公共 Agent 模块。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Configuration(proxyBeanMethods = false)
@Import(AgentPromptConfiguration.class)
public class AgentModuleConfiguration {
}
