package com.dingbang.myworld.agent.memory;

import com.dingbang.myworld.aiframework.api.ModelToolDefinition;
import com.dingbang.myworld.aiframework.model.Message;

import java.util.List;

/**
 * 估算单次模型输入并接收供应商真实用量用于校准。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
public interface TokenEstimator {
    /**
     * 估算模型消息和工具定义占用的输入 token 数。
     *
     * @param messages 模型消息
     * @param tools 模型可见的工具定义
     * @return 非负估算值
     */
    int estimate(List<Message> messages, List<ModelToolDefinition> tools);

    /**
     * 使用单次调用的真实输入用量更新后续估算。
     *
     * @param estimatedTokens 调用前对同一次请求的估算值
     * @param promptTokens 供应商报告的输入 token 数
     */
    default void observe(int estimatedTokens, long promptTokens) { }
}
