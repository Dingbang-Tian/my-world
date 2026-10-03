package com.dingbang.myworld.aiframework.embedding;

import java.net.URI;
import java.util.Objects;

/**
 * 不可变的文本或多模态向量模型连接配置。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public record EmbeddingModelConfig(String modelId, Protocol protocol, URI endpoint,
                                   String wireModel, String apiKey, Integer defaultDimensions,
                                   boolean fusionSupported, boolean fusionParameterRequired) {
    /**
     * 向量接口协议种类。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public enum Protocol { OPENAI, DASHSCOPE }

    /**
     * 校验本地标识、完整接口地址和授权信息。
     *
     * @param modelId 本地向量模型标识
     * @param protocol 服务端协议
     * @param endpoint 完整 HTTPS 或本地测试 HTTP 接口地址
     * @param wireModel 服务端模型名称
     * @param apiKey 访问密钥
     * @param defaultDimensions 默认维度
     * @param fusionSupported 模型是否支持融合向量
     * @param fusionParameterRequired 是否通过 enable_fusion 参数融合全部 contents
     */
    public EmbeddingModelConfig {
        if (modelId == null || modelId.isBlank() || wireModel == null || wireModel.isBlank()
                || apiKey == null || apiKey.isBlank() || (defaultDimensions != null && defaultDimensions <= 0)) {
            throw new IllegalArgumentException("向量模型配置无效");
        }
        Objects.requireNonNull(protocol, "向量协议不能为空");
        Objects.requireNonNull(endpoint, "向量地址不能为空");
        if (!("https".equalsIgnoreCase(endpoint.getScheme()) || "http".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("向量接口必须为无凭据的 HTTP(S) 绝对地址");
        }
        if (protocol == Protocol.OPENAI && (fusionSupported || fusionParameterRequired))
            throw new IllegalArgumentException("OpenAI 文本向量模型不能声明融合能力");
        if (fusionParameterRequired && !fusionSupported)
            throw new IllegalArgumentException("融合参数需要先启用融合能力");
    }

    /**
     * 创建不支持融合的模型实例。
     *
     * @param modelId 本地向量模型标识
     * @param protocol 服务端协议
     * @param endpoint 完整接口地址
     * @param wireModel 服务端模型名
     * @param apiKey 授权密钥
     * @param defaultDimensions 默认维度
     */
    public EmbeddingModelConfig(String modelId, Protocol protocol, URI endpoint,
                                String wireModel, String apiKey, Integer defaultDimensions) {
        this(modelId, protocol, endpoint, wireModel, apiKey, defaultDimensions, false, false);
    }

    /** @return 脱敏的模型标识 */
    @Override public String toString() { return "EmbeddingModelConfig{" + protocol + "/" + modelId + "}"; }
}
