package com.dingbang.myworld.aiapp.config.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 绑定应用中启用的 OpenAI Chat 兼容模型实例。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@ConfigurationProperties(prefix = "my-world.ai.chat")
public final class OpenAiChatProperties {

    /** 是否启用自研框架的 Chat 模型网关。 */
    private boolean enabled;

    /** 以本地 modelId 为键的模型实例。 */
    private Map<String, ModelProperties> models = new LinkedHashMap<>();

    /**
     * 返回网关启用状态。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置网关启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回按本地标识索引的模型属性。
     *
     * @return 模型属性映射
     */
    public Map<String, ModelProperties> getModels() {
        return models;
    }

    /**
     * 设置模型属性映射。
     *
     * @param models 模型属性映射
     */
    public void setModels(Map<String, ModelProperties> models) {
        this.models = models;
    }

    /**
     * 单个模型实例的连接与默认生成参数。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public static final class ModelProperties {

        /** 模型使用的聊天协议，默认 chat。 */
        private String protocol = "chat";
        /** 是否支持图片输入。 */
        private boolean imageEnabled;
        /** 是否支持推理选项。 */
        private boolean reasoningEnabled;
        /** 是否回传无签名的旧推理历史。 */
        private boolean forwardUnsignedThinking;
        /** 是否使用 Qwen 兼容推理开关。 */
        private boolean thinkingSwitchEnabled;
        /** 模型默认推理开关。 */
        private Boolean thinkingEnabled;

        /** 供应商标识。 */
        private String providerId;
        /** Chat Completions 完整接口地址。 */
        private String endpoint;
        /** 服务端模型名称。 */
        private String model;
        /** 授权密钥。 */
        private String apiKey;
        /** 默认采样温度。 */
        private Double temperature;
        /** 默认最大输出 token 数。 */
        private Integer maxCompletionTokens;
        /** 默认推理强度。 */
        private String reasoningEffort;
        /** 是否为兼容供应商回传推理历史。 */
        private boolean forwardReasoningContent;

        /** @return 聊天协议名称 */
        public String getProtocol() { return protocol; }
        /** @param protocol 聊天协议名称 */
        public void setProtocol(String protocol) { this.protocol = protocol; }
        /** @return 是否支持图片 */
        public boolean isImageEnabled() { return imageEnabled; }
        /** @param imageEnabled 是否支持图片 */
        public void setImageEnabled(boolean imageEnabled) { this.imageEnabled = imageEnabled; }
        /** @return 是否支持推理选项 */
        public boolean isReasoningEnabled() { return reasoningEnabled; }
        /** @param reasoningEnabled 是否支持推理选项 */
        public void setReasoningEnabled(boolean reasoningEnabled) { this.reasoningEnabled = reasoningEnabled; }
        /** @return 是否回传无签名的推理历史 */
        public boolean isForwardUnsignedThinking() { return forwardUnsignedThinking; }
        /** @param forwardUnsignedThinking 是否回传无签名的推理历史 */
        public void setForwardUnsignedThinking(boolean forwardUnsignedThinking) {
            this.forwardUnsignedThinking = forwardUnsignedThinking;
        }
        /** @return 是否允许 Qwen 推理开关 */
        public boolean isThinkingSwitchEnabled() { return thinkingSwitchEnabled; }
        /** @param thinkingSwitchEnabled 是否允许 Qwen 推理开关 */
        public void setThinkingSwitchEnabled(boolean thinkingSwitchEnabled) {
            this.thinkingSwitchEnabled = thinkingSwitchEnabled;
        }
        /** @return 默认推理开关 */
        public Boolean getThinkingEnabled() { return thinkingEnabled; }
        /** @param thinkingEnabled 默认推理开关 */
        public void setThinkingEnabled(Boolean thinkingEnabled) { this.thinkingEnabled = thinkingEnabled; }

        /**
         * 返回供应商标识。
         *
         * @return 供应商标识
         */
        public String getProviderId() { return providerId; }

        /**
         * 设置供应商标识。
         *
         * @param providerId 供应商标识
         */
        public void setProviderId(String providerId) { this.providerId = providerId; }

        /**
         * 返回接口地址。
         *
         * @return 完整接口地址
         */
        public String getEndpoint() { return endpoint; }

        /**
         * 设置接口地址。
         *
         * @param endpoint 完整接口地址
         */
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        /**
         * 返回服务端模型名称。
         *
         * @return 服务端模型名称
         */
        public String getModel() { return model; }

        /**
         * 设置服务端模型名称。
         *
         * @param model 服务端模型名称
         */
        public void setModel(String model) { this.model = model; }

        /**
         * 返回密钥供网关装配使用。
         *
         * @return 模型密钥
         */
        public String getApiKey() { return apiKey; }

        /**
         * 设置模型密钥。
         *
         * @param apiKey 模型密钥
         */
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        /**
         * 返回默认温度。
         *
         * @return 默认温度
         */
        public Double getTemperature() { return temperature; }

        /**
         * 设置默认温度。
         *
         * @param temperature 默认温度
         */
        public void setTemperature(Double temperature) { this.temperature = temperature; }

        /**
         * 返回默认最大输出 token 数。
         *
         * @return 默认上限
         */
        public Integer getMaxCompletionTokens() { return maxCompletionTokens; }

        /**
         * 设置默认最大输出 token 数。
         *
         * @param maxCompletionTokens 默认上限
         */
        public void setMaxCompletionTokens(Integer maxCompletionTokens) { this.maxCompletionTokens = maxCompletionTokens; }

        /**
         * 返回默认推理强度。
         *
         * @return 默认推理强度
         */
        public String getReasoningEffort() { return reasoningEffort; }

        /**
         * 设置默认推理强度。
         *
         * @param reasoningEffort 默认推理强度
         */
        public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }

        /**
         * 返回推理历史回传设置。
         *
         * @return 是否回传 reasoning_content
         */
        public boolean isForwardReasoningContent() { return forwardReasoningContent; }

        /**
         * 设置推理历史回传选项。
         *
         * @param forwardReasoningContent 是否回传 reasoning_content
         */
        public void setForwardReasoningContent(boolean forwardReasoningContent) {
            this.forwardReasoningContent = forwardReasoningContent;
        }
    }
}
