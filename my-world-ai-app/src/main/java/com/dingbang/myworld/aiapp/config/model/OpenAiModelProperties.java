package com.dingbang.myworld.aiapp.config.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 单个模型实例的连接与默认生成参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Getter
@Setter
public final class OpenAiModelProperties {
    /**
     * 模型使用的聊天协议，默认 chat。
     */
    private String protocol = "chat";
    /**
     * 是否支持图片输入。
     */
    private boolean imageEnabled;
    /**
     * 是否支持推理选项。
     */
    private boolean reasoningEnabled;
    /**
     * 是否回传无签名的旧推理历史。
     */
    private boolean forwardUnsignedThinking;
    /**
     * 是否使用 Qwen 兼容推理开关。
     */
    private boolean thinkingSwitchEnabled;
    /**
     * 模型默认推理开关。
     */
    private Boolean thinkingEnabled;
    /**
     * 供应商标识。
     */
    private String providerId;
    /**
     * Chat Completions 完整接口地址。
     */
    private String endpoint;
    /**
     * 服务端模型名称。
     */
    private String model;
    /**
     * 授权密钥。
     */
    private String apiKey;
    /**
     * 默认采样温度。
     */
    private Double temperature;
    /**
     * 默认最大输出 token 数。
     */
    private Integer maxCompletionTokens;
    /**
     * 默认推理强度。
     */
    private String reasoningEffort;
    /**
     * 是否为兼容供应商回传推理历史。
     */
    private boolean forwardReasoningContent;
}
