package com.dingbang.myworld.web.ai.codegen;

import com.dingbang.myworld.aiapp.codegen.application.CodegenTaskService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配代码生成 Web 入口的配置属性。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CodegenWebProperties.class)
@ConditionalOnProperty(prefix = "my-world.codegen", name = "enabled", havingValue = "true")
public class CodegenWebConfiguration {

    /**
     * 创建绑定可信所有者的代码生成 Web 应用服务。
     *
     * @param tasks 代码生成任务协调服务
     * @param properties Web 入口配置
     * @return 代码生成 Web 应用服务
     */
    @Bean
    public CodegenWebService codegenWebService(CodegenTaskService tasks,
                                               CodegenWebProperties properties) {
        return new CodegenWebService(tasks, properties);
    }
}
