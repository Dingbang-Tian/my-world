package com.dingbang.myworld.aiapp.config.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 绑定应用的独立向量模型实例配置。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@ConfigurationProperties(prefix = "my-world.ai.embedding")
public final class EmbeddingProperties {
    /** 是否启用向量网关。 */
    private boolean enabled;
    /** 以本地 modelId 索引的向量模型。 */
    private Map<String, ModelProperties> models = new LinkedHashMap<>();

    /** @return 是否启用网关 */
    public boolean isEnabled() { return enabled; }
    /** @param enabled 是否启用网关 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** @return 模型配置映射 */
    public Map<String, ModelProperties> getModels() { return models; }
    /** @param models 模型配置映射 */
    public void setModels(Map<String, ModelProperties> models) { this.models = models; }

    /**
     * 单个向量模型的可绑定属性。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public static final class ModelProperties {
        /** 协议名 openai 或 dashscope。 */
        private String protocol;
        /** 完整向量接口地址。 */
        private String endpoint;
        /** 服务端模型名。 */
        private String model;
        /** 密钥。 */
        private String apiKey;
        /** 默认输出维度。 */
        private Integer dimensions;
        /** 是否支持融合向量。 */
        private boolean fusionSupported;
        /** 融合是否通过 enable_fusion 参数作用于整个 contents。 */
        private boolean fusionParameterRequired;

        /** @return 协议名称 */
        public String getProtocol() { return protocol; }
        /** @param protocol 协议名称 */
        public void setProtocol(String protocol) { this.protocol = protocol; }
        /** @return 接口地址 */
        public String getEndpoint() { return endpoint; }
        /** @param endpoint 接口地址 */
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        /** @return 服务端模型名 */
        public String getModel() { return model; }
        /** @param model 服务端模型名 */
        public void setModel(String model) { this.model = model; }
        /** @return 授权密钥 */
        public String getApiKey() { return apiKey; }
        /** @param apiKey 授权密钥 */
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        /** @return 默认维度 */
        public Integer getDimensions() { return dimensions; }
        /** @param dimensions 默认维度 */
        public void setDimensions(Integer dimensions) { this.dimensions = dimensions; }
        /** @return 是否支持融合向量 */
        public boolean isFusionSupported() { return fusionSupported; }
        /** @param fusionSupported 是否支持融合向量 */
        public void setFusionSupported(boolean fusionSupported) { this.fusionSupported = fusionSupported; }
        /** @return 是否需要 enable_fusion 参数 */
        public boolean isFusionParameterRequired() { return fusionParameterRequired; }
        /** @param fusionParameterRequired 是否需要 enable_fusion 参数 */
        public void setFusionParameterRequired(boolean fusionParameterRequired) {
            this.fusionParameterRequired = fusionParameterRequired;
        }
    }
}
