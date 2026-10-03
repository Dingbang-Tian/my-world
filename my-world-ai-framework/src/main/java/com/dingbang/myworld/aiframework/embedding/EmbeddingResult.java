package com.dingbang.myworld.aiframework.embedding;

import java.util.List;

/**
 * 一次向量生成的有序结果及供应商用量。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public record EmbeddingResult(String modelId, String requestId, List<EmbeddingVector> vectors, EmbeddingUsage usage) {
    /**
     * 固定结果快照。
     *
     * @param modelId 本地模型标识
     * @param requestId 供应商请求标识，可为空
     * @param vectors 按输入索引排列的向量
     * @param usage 供应商报告的用量，可为空
     */
    public EmbeddingResult {
        if (modelId == null || modelId.isBlank() || vectors == null || vectors.isEmpty())
            throw new IllegalArgumentException("向量结果无效");
        vectors = List.copyOf(vectors);
    }

    /**
     * 兼容只需输入 token 数的调用方。
     *
     * @return 输入 token 数，供应商未报告时为 null
     */
    public Long inputTokens() { return usage == null ? null : usage.inputTokens(); }
}
