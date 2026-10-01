package com.dingbang.myworld.agent.config;

import com.dingbang.myworld.ai.config.SpringAiConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 装配公共 Agent 模块及其模型接入配置。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Configuration(proxyBeanMethods = false)
@Import(SpringAiConfiguration.class)
public class AgentModuleConfiguration {
}
