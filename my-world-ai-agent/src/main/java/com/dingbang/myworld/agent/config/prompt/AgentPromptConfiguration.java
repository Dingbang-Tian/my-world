package com.dingbang.myworld.agent.config.prompt;

import com.dingbang.myworld.agent.prompt.PromptRepository;
import com.dingbang.myworld.agent.prompt.PromptTemplateContributor;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.prompt.PromptTemplateSpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在 Spring 启动时装配并校验 Agent 提示词仓库。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentPromptProperties.class)
public class AgentPromptConfiguration {

    /**
     * 合并应用覆盖和项目 YAML 覆盖，创建不可变模板仓库。
     *
     * @param resourceLoader Spring 资源加载器
     * @param properties 项目配置
     * @param contributors 应用级模板覆盖提供者
     * @return 提示词仓库
     * @throws IllegalArgumentException 模板配置或资源非法时
     */
    @Bean
    public PromptRepository promptRepository(ResourceLoader resourceLoader, AgentPromptProperties properties,
                                             ObjectProvider<PromptTemplateContributor> contributors) {
        // 当前阶段只接入一个应用提供者；多个提供者会由 Spring 报出歧义。
        PromptTemplateContributor contributor = contributors.getIfAvailable();
        Map<String, PromptTemplateSpec> application = contributor == null
                ? Collections.emptyMap() : contributor.getTemplates();
        Map<String, PromptTemplateSpec> project = new LinkedHashMap<>();
        if (properties.getPrompts() != null) {
            for (Map.Entry<String, PromptTemplateSpec> entry : properties.getPrompts().entrySet()) {
                project.put("agent/" + entry.getKey(), entry.getValue());
            }
        }
        return new PromptTemplateRegistry(resourceLoader, application, project);
    }
}
