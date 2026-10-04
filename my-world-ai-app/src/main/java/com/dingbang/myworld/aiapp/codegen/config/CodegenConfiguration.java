package com.dingbang.myworld.aiapp.codegen.config;

import com.dingbang.myworld.agent.prompt.PromptRepository;
import com.dingbang.myworld.agent.memory.ContextPolicy;
import com.dingbang.myworld.agent.memory.TokenEstimator;
import com.dingbang.myworld.agent.memory.CalibratedTokenEstimator;
import com.dingbang.myworld.agent.persistence.RunJournal;
import com.dingbang.myworld.agent.session.SessionRepository;
import com.dingbang.myworld.aiapp.codegen.api.CodegenService;
import com.dingbang.myworld.aiapp.codegen.application.CodegenFactory;
import com.dingbang.myworld.aiapp.codegen.application.CodegenTaskService;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 仅在代码生成应用显式启用时按权限装配文件、命令和编排工具。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CodegenProperties.class)
public class CodegenConfiguration {
    /**
     * 为开发入口提供幂等运行和状态查询服务。
     *
     * @param codegen 已装配的代码生成服务
     * @param journal 可选持久化日志
     * @return 任务协调服务
     */
    @Bean
    @ConditionalOnProperty(prefix = "my-world.codegen", name = "enabled", havingValue = "true")
    public CodegenTaskService codegenTaskService(CodegenService codegen, RunJournal journal) {
        return new CodegenTaskService(codegen, journal);
    }

    /**
     * 校验可信配置并创建代码生成服务。
     *
     * @param gateway 模型入口
     * @param prompts 提示词仓库
     * @param properties 代码生成配置
     * @param sessions 会话仓库
     * @param journal 运行日志
     * @param estimators 可选的供应商专用输入估算器
     * @return 应用服务
     */
    @Bean
    @ConditionalOnProperty(prefix = "my-world.codegen", name = "enabled", havingValue = "true")
    public CodegenService codegenService(ModelGateway gateway, PromptRepository prompts,
                                         CodegenProperties properties, SessionRepository sessions,
                                         RunJournal journal, ObjectProvider<TokenEstimator> estimators) {
        if (properties.getModelId() == null || properties.getModelId().isBlank()
                || properties.getWorkspace() == null || properties.getWorkspace().isBlank()) {
            throw new IllegalArgumentException("启用 codegen 时必须配置 model-id 和 workspace");
        }
        if (properties.isCommandEnabled()
                && (properties.getToolOutputDirectory() == null
                || properties.getToolOutputDirectory().isBlank()
                || !Path.of(properties.getToolOutputDirectory()).isAbsolute())) {
            throw new IllegalArgumentException("启用命令工具时必须配置绝对的 tool-output-directory");
        }
        // 配置提供的工作目录。
        Path workspace = Path.of(properties.getWorkspace());
        if (!workspace.isAbsolute()) {
            throw new IllegalArgumentException("codegen workspace 必须是绝对路径");
        }
        return new CodegenFactory().create(gateway, prompts, properties.getModelId(),
                workspace, properties.isWriteEnabled(), properties.isCommandEnabled(),
                properties.getCommandEnvironmentAllowlist(), properties.isPlanEnabled(),
                properties.isSubAgentEnabled(), new ContextPolicy(properties.getContextWindowTokens(),
                        properties.getSummaryTriggerRounds(), properties.getSummaryTriggerTokens(),
                        properties.getReserveOutputTokens(), properties.getMaxSummaryTokens(),
                        properties.getRecentHistoryRounds(), properties.getRecentHistoryTokens()),
                sessions, journal, properties.isCommandEnabled()
                        ? Path.of(properties.getToolOutputDirectory()) : workspace,
                estimators.getIfAvailable(CalibratedTokenEstimator::new));
    }
}
