package com.dingbang.myworld.aiapp.config.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 绑定应用中启用的 OpenAI Chat 兼容模型实例。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "my-world.ai.chat")
public final class OpenAiChatProperties {
    /**
     * 是否启用自研框架的 Chat 模型网关。
     */
    private boolean enabled;
    /**
     * 以本地 modelId 为键的模型实例。
     */
    private Map<String, OpenAiModelProperties> models = new LinkedHashMap<>();
}
