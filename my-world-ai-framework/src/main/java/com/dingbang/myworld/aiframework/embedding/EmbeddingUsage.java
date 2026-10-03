package com.dingbang.myworld.aiframework.embedding;

/**
 * 保留向量供应商按模态报告的可选 token 用量。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public record EmbeddingUsage(Long inputTokens, Long outputTokens, Long totalTokens,
                             Long imageTokens, Long textTokens) {
    /**
     * 校验已报告的 token 用量均为非负数。
     *
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @param totalTokens 总 token 数
     * @param imageTokens 图片和视频 token 数
     * @param textTokens 文本 token 数
     */
    public EmbeddingUsage {
        if (negative(inputTokens) || negative(outputTokens) || negative(totalTokens)
                || negative(imageTokens) || negative(textTokens)) {
            throw new IllegalArgumentException("向量 token 用量不能为负数");
        }
    }

    /**
     * 判断可选用量是否为负数。
     *
     * @param value 可选 token 数
     * @return 负数时为 true
     */
    private static boolean negative(Long value) { return value != null && value < 0; }
}
