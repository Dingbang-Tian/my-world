package com.dingbang.myworld.aiframework.protocol.openai;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import lombok.AccessLevel;
import lombok.Getter;

import java.net.URI;
import java.util.Objects;

/**
 * 一个 OpenAI Chat 兼容模型实例的不可变连接配置。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Getter
public final class OpenAiChatModelConfig {

    /**
     * 供 Agent 选择的本地模型标识。
     */
    private final String modelId;

    /**
     * 供应商标识，仅供诊断，不决定协议解析。
     */
    private final String providerId;

    /**
     * Chat Completions 接口的完整地址。
     */
    private final URI endpoint;

    /**
     * 服务端识别的模型名称。
     */
    private final String wireModel;

    /**
     * 请求用密钥，不进入日志或 toString。
     */
    @Getter(AccessLevel.PACKAGE)
    private final String apiKey;

    /**
     * 模型实例默认生成选项。
     */
    private final ModelOptions defaultOptions;

    /**
     * 兼容供应商是否要求把推理文本写回助手历史。
     */
    private final boolean forwardReasoningContent;

    /**
     * 模型实例是否允许图片输入。
     */
    private final boolean imageEnabled;

    /**
     * 是否接受 Qwen 兼容的 chat_template_kwargs 推理开关。
     */
    private final boolean thinkingSwitchEnabled;

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
        this(modelId, providerId, endpoint, wireModel, apiKey, defaultOptions,
                forwardReasoningContent, false, false);
    }

    /**
     * 创建带显式图片能力声明的聊天模型配置。
     *
     * @param modelId 本地模型标识
     * @param providerId 供应商标识
     * @param endpoint Chat Completions 完整接口地址
     * @param wireModel 服务端模型名称
     * @param apiKey 授权密钥
     * @param defaultOptions 默认生成选项
     * @param forwardReasoningContent 是否回传兼容供应商的推理历史
     * @param imageEnabled 是否允许图片输入
     */
    public OpenAiChatModelConfig(String modelId, String providerId, URI endpoint,
                                 String wireModel, String apiKey, ModelOptions defaultOptions,
                                 boolean forwardReasoningContent, boolean imageEnabled) {
        this(modelId, providerId, endpoint, wireModel, apiKey, defaultOptions,
                forwardReasoningContent, imageEnabled, false);
    }

    /**
     * 创建带显式图片与 Qwen 推理开关能力的实例。
     *
     * @param modelId 本地模型标识
     * @param providerId 供应商标识
     * @param endpoint 完整接口地址
     * @param wireModel 服务端模型名称
     * @param apiKey 授权密钥
     * @param defaultOptions 模型默认选项
     * @param forwardReasoningContent 是否回传推理历史
     * @param imageEnabled 是否允许图片输入
     * @param thinkingSwitchEnabled 是否允许 Qwen 推理开关
     */
    public OpenAiChatModelConfig(String modelId, String providerId, URI endpoint,
                                 String wireModel, String apiKey, ModelOptions defaultOptions,
                                 boolean forwardReasoningContent, boolean imageEnabled,
                                 boolean thinkingSwitchEnabled) {
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
        this.imageEnabled = imageEnabled;
        this.thinkingSwitchEnabled = thinkingSwitchEnabled;
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
