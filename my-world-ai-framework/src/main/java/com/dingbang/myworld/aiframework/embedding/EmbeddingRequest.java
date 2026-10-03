package com.dingbang.myworld.aiframework.embedding;

import java.util.List;
import java.util.Objects;

/**
 * 一次向量生成调用的模型、输入和可选维度。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public record EmbeddingRequest(String modelId, List<EmbeddingInput> inputs, Integer dimensions, boolean fusion) {
    /**
     * 验证模型、输入与维度，并固定输入快照。
     *
     * @param modelId 向量模型标识
     * @param inputs 非空输入列表
     * @param dimensions 输出维度，null 表示模型默认值
     * @param fusion 是否请求融合向量
     */
    public EmbeddingRequest {
        if (modelId == null || modelId.isBlank() || inputs == null || inputs.isEmpty()
                || (dimensions != null && dimensions <= 0)) throw new IllegalArgumentException("向量请求无效");
        inputs.forEach(Objects::requireNonNull);
        inputs = List.copyOf(inputs);
    }
}
