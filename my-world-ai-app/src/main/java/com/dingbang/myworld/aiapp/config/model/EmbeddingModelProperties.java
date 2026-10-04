package com.dingbang.myworld.aiapp.config.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 单个向量模型的可绑定属性。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Getter
@Setter
public final class EmbeddingModelProperties {
    /**
     * 协议名 openai 或 dashscope。
     */
    private String protocol;
    /**
     * 完整向量接口地址。
     */
    private String endpoint;
    /**
     * 服务端模型名。
     */
    private String model;
    /**
     * 密钥。
     */
    private String apiKey;
    /**
     * 默认输出维度。
     */
    private Integer dimensions;
    /**
     * 是否支持融合向量。
     */
    private boolean fusionSupported;
    /**
     * 融合是否通过 enable_fusion 参数作用于整个 contents。
     */
    private boolean fusionParameterRequired;
}
