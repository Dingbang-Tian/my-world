package com.dingbang.myworld.aiframework.api;

import lombok.Data;

/**
 * 一次模型调用的最终 token 用量。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class ModelTokenUsage {

    /**
     * 输入 token 数。
     */
    private final long promptTokens;

    /**
     * 输出 token 数。
     */
    private final long completionTokens;

    /**
     * 总 token 数。
     */
    private final long totalTokens;

    /**
     * 输出中包含的推理 token 数，供应商未提供时为 null。
     */
    private final Long reasoningTokens;

    /**
     * 固定非负的用量数据。
     *
     * @param promptTokens 输入 token 数
     * @param completionTokens 输出 token 数
     * @param totalTokens 总 token 数
     * @param reasoningTokens 推理 token 数，未知时为 null
     */
    public ModelTokenUsage(long promptTokens, long completionTokens, long totalTokens, Long reasoningTokens) {
        if (promptTokens < 0 || completionTokens < 0 || totalTokens < 0
                || (reasoningTokens != null && reasoningTokens < 0)) {
            throw new IllegalArgumentException("token 用量不能为负数");
        }
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.reasoningTokens = reasoningTokens;
    }

    /**
     * 将另一轮用量加到当前用量，未知推理用量继续保持未知。
     *
     * @param other 另一轮完整用量
     * @return 两轮用量之和
     * @throws ArithmeticException token 合计超出 long 范围时
     */
    public ModelTokenUsage plus(ModelTokenUsage other) {
        return new ModelTokenUsage(Math.addExact(promptTokens, other.promptTokens),
                Math.addExact(completionTokens, other.completionTokens),
                Math.addExact(totalTokens, other.totalTokens),
                reasoningTokens == null || other.reasoningTokens == null ? null
                        : Math.addExact(reasoningTokens, other.reasoningTokens));
    }
}
