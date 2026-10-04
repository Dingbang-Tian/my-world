package com.dingbang.myworld.aiframework.embedding;

import lombok.Value;

/**
 * 保留向量供应商按模态报告的可选 token 用量。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Value
public class EmbeddingUsage {

    /**
     * 输入 token 数。
     */
    Long inputTokens;

    /**
     * 输出 token 数。
     */
    Long outputTokens;

    /**
     * 总 token 数。
     */
    Long totalTokens;

    /**
     * 图片和视频 token 数。
     */
    Long imageTokens;

    /**
     * 文本 token 数。
     */
    Long textTokens;
    /**
     * 校验已报告的 token 用量均为非负数。
     *
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @param totalTokens 总 token 数
     * @param imageTokens 图片和视频 token 数
     * @param textTokens 文本 token 数
     */
    public EmbeddingUsage(Long inputTokens, Long outputTokens, Long totalTokens,
                          Long imageTokens, Long textTokens) {
        if (negative(inputTokens) || negative(outputTokens) || negative(totalTokens)
                || negative(imageTokens) || negative(textTokens)) {
            throw new IllegalArgumentException("向量 token 用量不能为负数");
        }
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.imageTokens = imageTokens;
        this.textTokens = textTokens;
    }

    /**
     * 判断可选用量是否为负数。
     *
     * @param value 可选 token 数
     * @return 负数时为 true
     */
    private static boolean negative(Long value) {
        return value != null && value < 0;
    }
}
