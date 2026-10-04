package com.dingbang.myworld.agent.memory;

import lombok.Data;

/**
 * 定义模型上下文窗口和历史摘要的可信阈值。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class ContextPolicy {
    /**
     * 模型输入和预留输出合计的估算窗口。
     */
    private final int windowTokens;
    /**
     * 未覆盖交换达到此数量时触发摘要。
     */
    private final int triggerRounds;
    /**
     * 当前输入估算达到此数量时触发摘要。
     */
    private final int triggerTokens;
    /**
     * 为普通回答预留的 token 数。
     */
    private final int reserveOutputTokens;
    /**
     * 摘要最多允许的估算 token 数。
     */
    private final int maxSummaryTokens;

    /**
     * 创建并校验摘要策略。
     *
     * @param windowTokens 模型上下文窗口
     * @param triggerRounds 未覆盖轮次阈值
     * @param triggerTokens 输入 token 阈值
     * @param reserveOutputTokens 回答预留 token
     * @param maxSummaryTokens 摘要长度上限
     */
    public ContextPolicy(int windowTokens, int triggerRounds, int triggerTokens,
                         int reserveOutputTokens, int maxSummaryTokens) {
        if (windowTokens < 64 || triggerRounds < 1 || triggerTokens < 1
                || reserveOutputTokens < 1 || maxSummaryTokens < 1
                || reserveOutputTokens + maxSummaryTokens >= windowTokens
                || triggerTokens > windowTokens - reserveOutputTokens) {
            throw new IllegalArgumentException("上下文策略无效");
        }
        this.windowTokens = windowTokens;
        this.triggerRounds = triggerRounds;
        this.triggerTokens = triggerTokens;
        this.reserveOutputTokens = reserveOutputTokens;
        this.maxSummaryTokens = maxSummaryTokens;
    }

    /**
     * 返回适合学习项目的默认策略。
     *
     * @return 默认策略
     */
    public static ContextPolicy defaults() {
        return new ContextPolicy(32768, 20, 24576, 4096, 1024);
    }
}
