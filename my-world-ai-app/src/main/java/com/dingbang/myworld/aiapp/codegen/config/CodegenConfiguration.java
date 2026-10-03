package com.dingbang.myworld.aiapp.codegen.config;

import com.dingbang.myworld.agent.prompt.PromptRepository;
import com.dingbang.myworld.aiapp.codegen.api.CodegenService;
import com.dingbang.myworld.aiapp.codegen.application.CodegenFactory;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 仅在代码生成应用显式启用时按权限装配文件与命令工具。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CodegenProperties.class)
public class CodegenConfiguration {
    /**
     * 校验可信配置并创建代码生成服务。
     *
     * @param gateway 模型入口
     * @param prompts 提示词仓库
     * @param properties 代码生成配置
     * @return 应用服务
     */
    @Bean
    @ConditionalOnProperty(prefix = "my-world.codegen", name = "enabled", havingValue = "true")
    public CodegenService codegenService(ModelGateway gateway, PromptRepository prompts,
                                         CodegenProperties properties) {
        if (properties.getModelId() == null || properties.getModelId().isBlank()
                || properties.getWorkspace() == null || properties.getWorkspace().isBlank()) {
            throw new IllegalArgumentException("启用 codegen 时必须配置 model-id 和 workspace");
        }
        /** 配置提供的工作目录。 */
        Path workspace = Path.of(properties.getWorkspace());
        if (!workspace.isAbsolute()) {
            throw new IllegalArgumentException("codegen workspace 必须是绝对路径");
        }
        return new CodegenFactory().create(gateway, prompts, properties.getModelId(),
                workspace, properties.isWriteEnabled(), properties.isCommandEnabled(),
                properties.getCommandEnvironmentAllowlist());
    }
}
