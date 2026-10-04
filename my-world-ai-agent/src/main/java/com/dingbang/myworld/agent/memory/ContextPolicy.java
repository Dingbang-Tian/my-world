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
     * 历史摘要后仍保留的最近完整交换数。
     */
    private final int recentHistoryRounds;
    /**
     * 最近完整交换可占用的估算输入上限。
     */
    private final int recentHistoryTokens;

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
        this(windowTokens, triggerRounds, triggerTokens, reserveOutputTokens, maxSummaryTokens, 0, 0);
    }

    /**
     * 创建带最近完整交换窗口的摘要策略。
     *
     * @param windowTokens 模型上下文窗口
     * @param triggerRounds 未覆盖轮次阈值
     * @param triggerTokens 输入估算触发阈值
     * @param reserveOutputTokens 回答预留空间
     * @param maxSummaryTokens 摘要上限
     * @param recentHistoryRounds 保留的最近完整交换数
     * @param recentHistoryTokens 最近完整交换估算上限
     */
    public ContextPolicy(int windowTokens, int triggerRounds, int triggerTokens,
                         int reserveOutputTokens, int maxSummaryTokens,
                         int recentHistoryRounds, int recentHistoryTokens) {
        if (windowTokens < 64 || triggerRounds < 1 || triggerTokens < 1
                || reserveOutputTokens < 1 || maxSummaryTokens < 1
                || reserveOutputTokens + maxSummaryTokens >= windowTokens
                || triggerTokens > windowTokens - reserveOutputTokens
                || recentHistoryRounds < 0 || recentHistoryTokens < 0
                || (recentHistoryRounds > 0 && recentHistoryTokens < 1)
                || recentHistoryTokens >= windowTokens - reserveOutputTokens) {
            throw new IllegalArgumentException("上下文策略无效");
        }
        this.windowTokens = windowTokens;
        this.triggerRounds = triggerRounds;
        this.triggerTokens = triggerTokens;
        this.reserveOutputTokens = reserveOutputTokens;
        this.maxSummaryTokens = maxSummaryTokens;
        this.recentHistoryRounds = recentHistoryRounds;
        this.recentHistoryTokens = recentHistoryTokens;
    }

    /**
     * 返回适合学习项目的默认策略。
     *
     * @return 默认策略
     */
    public static ContextPolicy defaults() {
        return new ContextPolicy(32768, 20, 24576, 4096, 1024, 3, 4096);
    }
}
