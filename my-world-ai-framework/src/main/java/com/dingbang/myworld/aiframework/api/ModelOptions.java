package com.dingbang.myworld.aiframework.api;

import lombok.Data;

/**
 * 单次模型调用的可选生成参数，空值表示沿用模型实例默认值。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class ModelOptions {

    /** 采样温度，取值为 0 到 2。 */
    private final Double temperature;

    /** 最大生成 token 数，必须为正数。 */
    private final Integer maxCompletionTokens;

    /** 推理强度，由具体协议验证支持的取值。 */
    private final String reasoningEffort;

    /** 是否显式启用模型推理；空值表示沿用模型默认值。 */
    private final Boolean thinkingEnabled;

    /**
     * 创建无覆盖值的模型选项。
     *
     * @return 所有字段均为空的选项
     */
    public static ModelOptions empty() {
        return new ModelOptions(null, null, null, null);
    }

    /**
     * 校验并固定调用选项。
     *
     * @param temperature 采样温度，null 表示继承
     * @param maxCompletionTokens 最大生成 token 数，null 表示继承
     * @param reasoningEffort 推理强度，null 表示继承
     */
    public ModelOptions(Double temperature, Integer maxCompletionTokens, String reasoningEffort) {
        this(temperature, maxCompletionTokens, reasoningEffort, null);
    }

    /**
     * 固定完整的调用选项。
     *
     * @param temperature 采样温度
     * @param maxCompletionTokens 最大生成 token 数
     * @param reasoningEffort 推理强度
     * @param thinkingEnabled 是否显式启用推理
     */
    public ModelOptions(Double temperature, Integer maxCompletionTokens, String reasoningEffort,
                        Boolean thinkingEnabled) {
        if (temperature != null && (!Double.isFinite(temperature) || temperature < 0 || temperature > 2)) {
            throw new IllegalArgumentException("temperature 必须在 0 到 2 之间");
        }
        if (maxCompletionTokens != null && maxCompletionTokens <= 0) {
            throw new IllegalArgumentException("maxCompletionTokens 必须为正数");
        }
        if (reasoningEffort != null && reasoningEffort.isBlank()) {
            throw new IllegalArgumentException("reasoningEffort 不能为空白文本");
        }
        this.temperature = temperature;
        this.maxCompletionTokens = maxCompletionTokens;
        this.reasoningEffort = reasoningEffort;
        this.thinkingEnabled = thinkingEnabled;
    }

    /**
     * 用本次调用的非空值覆盖模型默认值。
     *
     * @param defaults 模型实例默认值
     * @return 合并后的新选项
     */
    public ModelOptions overlay(ModelOptions defaults) {
        return new ModelOptions(temperature != null ? temperature : defaults.temperature,
                maxCompletionTokens != null ? maxCompletionTokens : defaults.maxCompletionTokens,
                reasoningEffort != null ? reasoningEffort : defaults.reasoningEffort,
                thinkingEnabled != null ? thinkingEnabled : defaults.thinkingEnabled);
    }
}
