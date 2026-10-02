package com.dingbang.myworld.aiframework.protocol.openai;

import com.dingbang.myworld.aiframework.api.ModelOptions;

import java.net.URI;
import java.util.Objects;

/**
 * 一个 OpenAI Chat 兼容模型实例的不可变连接配置。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class OpenAiChatModelConfig {

    /** 供 Agent 选择的本地模型标识。 */
    private final String modelId;

    /** 供应商标识，仅供诊断，不决定协议解析。 */
    private final String providerId;

    /** Chat Completions 接口的完整地址。 */
    private final URI endpoint;

    /** 服务端识别的模型名称。 */
    private final String wireModel;

    /** 请求用密钥，不进入日志或 toString。 */
    private final String apiKey;

    /** 模型实例默认生成选项。 */
    private final ModelOptions defaultOptions;

    /** 兼容供应商是否要求把推理文本写回助手历史。 */
    private final boolean forwardReasoningContent;

    /**
     * 创建可复用的模型实例配置。
     *
     * @param modelId 本地模型标识
     * @param providerId 供应商标识
     * @param endpoint Chat Completions 完整接口地址
     * @param wireModel 服务端模型名称
     * @param apiKey 授权密钥
     * @param defaultOptions 模型实例默认选项
     */
    public OpenAiChatModelConfig(String modelId, String providerId, URI endpoint,
                                 String wireModel, String apiKey, ModelOptions defaultOptions) {
        this(modelId, providerId, endpoint, wireModel, apiKey, defaultOptions, false);
    }

    /**
     * 创建可选择回传推理历史字段的模型实例配置。
     *
     * @param modelId 本地模型标识
     * @param providerId 供应商标识
     * @param endpoint Chat Completions 完整接口地址
     * @param wireModel 服务端模型名称
     * @param apiKey 授权密钥
     * @param defaultOptions 模型实例默认选项
     * @param forwardReasoningContent 是否向兼容供应商回传 reasoning_content
     */
    public OpenAiChatModelConfig(String modelId, String providerId, URI endpoint,
                                 String wireModel, String apiKey, ModelOptions defaultOptions,
                                 boolean forwardReasoningContent) {
        if (modelId == null || modelId.isBlank() || providerId == null || providerId.isBlank()
                || wireModel == null || wireModel.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("模型、供应商、服务端模型和密钥不能为空");
        }
        this.endpoint = Objects.requireNonNull(endpoint, "模型接口地址不能为 null");
        if (!endpoint.isAbsolute() || !("http".equals(endpoint.getScheme())
                || "https".equals(endpoint.getScheme())) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("模型接口地址必须是无用户信息的 HTTP(S) 绝对地址");
        }
        this.modelId = modelId;
        this.providerId = providerId;
        this.wireModel = wireModel;
        this.apiKey = apiKey;
        this.defaultOptions = Objects.requireNonNull(defaultOptions, "默认选项不能为 null");
        this.forwardReasoningContent = forwardReasoningContent;
    }

    /**
     * 返回本地模型标识。
     *
     * @return 本地模型标识
     */
    public String getModelId() {
        return modelId;
    }

    /**
     * 返回供应商标识。
     *
     * @return 供应商标识
     */
    public String getProviderId() {
        return providerId;
    }

    /**
     * 返回接口地址。
     *
     * @return 完整接口地址
     */
    public URI getEndpoint() {
        return endpoint;
    }

    /**
     * 返回服务端模型名称。
     *
     * @return 服务端模型名称
     */
    public String getWireModel() {
        return wireModel;
    }

    /**
     * 返回授权密钥供 HTTP 层使用。
     *
     * @return 授权密钥
     */
    String getApiKey() {
        return apiKey;
    }

    /**
     * 返回实例默认选项。
     *
     * @return 默认生成选项
     */
    public ModelOptions getDefaultOptions() {
        return defaultOptions;
    }

    /**
     * 返回是否回传兼容供应商的推理历史字段。
     *
     * @return 是否回传 reasoning_content
     */
    public boolean isForwardReasoningContent() {
        return forwardReasoningContent;
    }

    /**
     * 仅显示非敏感的模型身份。
     *
     * @return 脱敏配置说明
     */
    @Override
    public String toString() {
        return "OpenAiChatModelConfig{" + providerId + "/" + modelId + "}";
    }
}
