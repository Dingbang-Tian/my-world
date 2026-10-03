package com.dingbang.myworld.aiframework.embedding;

/**
 * 与聊天模型分离的同步向量生成入口。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public interface EmbeddingGateway {
    /**
     * 执行一次向量调用。
     *
     * @param request 已验证的向量请求
     * @return 包含索引、向量和用量的结果
     */
    EmbeddingResult embed(EmbeddingRequest request);
}
