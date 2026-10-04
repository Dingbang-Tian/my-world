package com.dingbang.myworld.agent.api;

import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.runtime.DefaultAgentService;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 供普通 JAR 消费者创建无需 Spring 容器的基础 Agent 服务。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgentSdk {

    /**
     * 使用内置系统与摘要模板创建无工具的 Agent 服务。
     *
     * @param gateway 单次模型入口
     * @param definition 可信应用定义，工具和技能列表必须为空
     * @return 可准备及执行任务的 Agent 服务
     * @throws IllegalArgumentException 定义包含此基础入口未授权的工具或技能时
     */
    public static AgentService create(ModelGateway gateway, AgentDefinition definition) {
        Objects.requireNonNull(gateway, "模型入口不能为空");
        Objects.requireNonNull(definition, "Agent 定义不能为空");
        if (!definition.getToolIds().isEmpty() || !definition.getSkillIds().isEmpty()) {
            throw new IllegalArgumentException("基础 SDK 入口只支持无工具定义");
        }
        return new DefaultAgentService(gateway,
                new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of()),
                List.of(definition));
    }
}
