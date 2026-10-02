package com.dingbang.myworld.agent.config.prompt;

import com.dingbang.myworld.agent.prompt.PromptTemplateSpec;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目 YAML 中的 Agent 提示词覆盖配置。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
@ConfigurationProperties(prefix = "my-world.agent")
public final class AgentPromptProperties {

    /**
     * 以 system 等短名称为键的项目模板覆盖。
     */
    private Map<String, PromptTemplateSpec> prompts = new LinkedHashMap<>();
}
