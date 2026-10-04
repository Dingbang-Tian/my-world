package com.dingbang.myworld.aiframework.protocol;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import lombok.Value;

import java.net.URI;
import java.util.Objects;

/**
 * Responses 或 Anthropic Messages 模型实例的不可变能力与连接配置。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Value
public class MultiProtocolModelConfig {

    /**
     * 供应用选择的本地模型标识。
     */
    String modelId;

    /**
     * 服务端实际使用的协议。
     */
    MultiProtocolType protocol;

    /**
     * 模型接口完整地址。
     */
    URI endpoint;

    /**
     * 服务端识别的模型名称。
     */
    String wireModel;

    /**
     * 接口密钥，不进入日志和字符串表示。
     */
    String apiKey;

    /**
     * 模型级默认生成选项。
     */
    ModelOptions defaultOptions;

    /**
     * 是否接受图片输入。
     */
    boolean imageEnabled;

    /**
     * 是否接受推理选项。
     */
    boolean reasoningEnabled;

    /**
     * 是否回传无签名推理历史。
     */
    boolean forwardUnsignedThinking;

    /**
     * 校验模型身份、地址和能力选项。
     *
     * @param modelId 本地模型标识
     * @param protocol 实际协议
     * @param endpoint 完整接口地址
     * @param wireModel 服务端模型名称
     * @param apiKey 接口密钥
     * @param defaultOptions 模型默认选项
     * @param imageEnabled 是否接受图片输入
     * @param reasoningEnabled 是否接受推理选项
     * @param forwardUnsignedThinking 是否回传旧历史中无签名的推理文本
     */
    public MultiProtocolModelConfig(String modelId, MultiProtocolType protocol, URI endpoint,
                                    String wireModel, String apiKey, ModelOptions defaultOptions,
                                    boolean imageEnabled, boolean reasoningEnabled,
                                    boolean forwardUnsignedThinking) {
        if (modelId == null || modelId.isBlank() || wireModel == null || wireModel.isBlank()
                || apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("模型实例配置无效");
        Objects.requireNonNull(protocol, "协议不能为空");
        Objects.requireNonNull(endpoint, "接口地址不能为空");
        Objects.requireNonNull(defaultOptions, "默认选项不能为空");
        if (!("https".equalsIgnoreCase(endpoint.getScheme()) || "http".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("接口地址必须是无凭据的 HTTP(S) 地址");
        }
        this.modelId = modelId;
        this.protocol = protocol;
        this.endpoint = endpoint;
        this.wireModel = wireModel;
        this.apiKey = apiKey;
        this.defaultOptions = defaultOptions;
        this.imageEnabled = imageEnabled;
        this.reasoningEnabled = reasoningEnabled;
        this.forwardUnsignedThinking = forwardUnsignedThinking;
    }

    /**
     * @return 不含密钥的配置摘要
     */
    @Override
    public String toString() {
        return "MultiProtocolModelConfig{" + protocol + "/" + modelId + "}";
    }
}
