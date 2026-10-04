package com.dingbang.myworld.aiapp.config.model;

import com.dingbang.myworld.aiframework.embedding.EmbeddingProtocol;

import com.dingbang.myworld.aiframework.embedding.EmbeddingGateway;
import com.dingbang.myworld.aiframework.embedding.EmbeddingModelConfig;
import com.dingbang.myworld.aiframework.embedding.HttpEmbeddingGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 在显式启用时注册独立于聊天网关的向量服务。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfiguration {
    /**
     * 创建不可变的向量模型注册表。
     *
     * @param properties 应用配置
     * @return 向量网关
     */
    @Bean
    @ConditionalOnProperty(prefix = "my-world.ai.embedding", name = "enabled", havingValue = "true")
    public EmbeddingGateway embeddingGateway(EmbeddingProperties properties) {
        if (properties.getModels() == null || properties.getModels().isEmpty())
            throw new IllegalArgumentException("启用向量网关时必须配置模型");
        // 待注册的模型列表。
        List<EmbeddingModelConfig> configs = new ArrayList<>();
        for (Map.Entry<String, EmbeddingModelProperties> entry : properties.getModels().entrySet()) {
            // 当前模型属性。
            EmbeddingModelProperties model = entry.getValue();
            // 显式选择的向量协议。
            EmbeddingProtocol protocol = switch (model.getProtocol().toLowerCase()) {
                case "openai" -> EmbeddingProtocol.OPENAI;
                case "dashscope" -> EmbeddingProtocol.DASHSCOPE;
                default -> throw new IllegalArgumentException("未知向量协议: " + model.getProtocol());
            };
            configs.add(new EmbeddingModelConfig(entry.getKey(), protocol, URI.create(model.getEndpoint()),
                    model.getModel(), model.getApiKey(), model.getDimensions(),
                    model.isFusionSupported(), model.isFusionParameterRequired()));
        }
        return new HttpEmbeddingGateway(configs);
    }
}
