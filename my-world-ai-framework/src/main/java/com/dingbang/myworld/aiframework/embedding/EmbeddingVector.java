package com.dingbang.myworld.aiframework.embedding;

import java.util.List;

/**
 * 带输入索引和类型的非空浮点向量。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public record EmbeddingVector(int index, String type, List<Double> values) {
    /**
     * 验证向量各分量。
     *
     * @param index 输入索引
     * @param type 供应商报告的向量类型，可为空
     * @param values 非空有限浮点数列表
     */
    public EmbeddingVector {
        if (index < 0 || values == null || values.isEmpty()
                || values.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("向量数据无效");
        }
        values = List.copyOf(values);
    }

    /** @return 向量维度 */
    public int dimension() { return values.size(); }
}
