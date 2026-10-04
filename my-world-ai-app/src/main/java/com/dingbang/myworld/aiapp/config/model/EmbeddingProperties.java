package com.dingbang.myworld.aiapp.config.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 绑定应用的独立向量模型实例配置。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "my-world.ai.embedding")
public final class EmbeddingProperties {
    /**
     * 是否启用向量网关。
     */
    private boolean enabled;
    /**
     * 以本地 modelId 索引的向量模型。
     */
    private Map<String, EmbeddingModelProperties> models = new LinkedHashMap<>();
}
