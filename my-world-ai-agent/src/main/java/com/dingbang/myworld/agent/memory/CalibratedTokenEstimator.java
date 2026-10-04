package com.dingbang.myworld.agent.memory;

import com.dingbang.myworld.aiframework.api.ModelToolDefinition;
import com.dingbang.myworld.aiframework.model.Message;

import java.util.List;
import java.util.Objects;

/**
 * 根据供应商输入用量校准字节估算，保留安全余量。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
public final class CalibratedTokenEstimator implements TokenEstimator {
    /** 校准前的保守估算器。 */
    private final TokenEstimator base;
    /** 实测输入与字节估算的平滑比例。 */
    private double ratio = 1.0;
    /** 是否已有可信的大样本。 */
    private boolean observed;

    /**
     * 使用默认字节估算器。
     */
    public CalibratedTokenEstimator() {
        this(new ByteTokenEstimator());
    }

    /**
     * 使用可替换的基础估算器。
     *
     * @param base 基础估算器
     */
    public CalibratedTokenEstimator(TokenEstimator base) {
        this.base = Objects.requireNonNull(base, "基础估算器不能为空");
    }

    /**
     * 应用实测比例及安全余量估算输入。
     *
     * @param messages 模型消息
     * @param tools 工具定义
     * @return 校准后的输入估算
     */
    @Override
    public synchronized int estimate(List<Message> messages, List<ModelToolDefinition> tools) {
        /** 基础字节估算。 */
        int raw = base.estimate(messages, tools);
        /** 使用实际用量时额外预留四分之一容量。 */
        double adjusted = raw * (observed ? ratio * 1.25 : 1.0);
        return adjusted >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(adjusted);
    }

    /**
     * 仅使用足够大的真实用量样本，避免短测试或小请求造成比例抖动。
     *
     * @param estimatedTokens 本次调用前的估算值
     * @param promptTokens 供应商输入用量
     */
    @Override
    public synchronized void observe(int estimatedTokens, long promptTokens) {
        if (estimatedTokens < 512 || promptTokens < 128) return;
        /** 从带安全余量的估算还原实测 token 与原始字节的比例。 */
        double effectiveFactor = observed ? ratio * 1.25 : 1.0;
        /** 将异常样本限制在合理范围。 */
        double sample = Math.max(0.25, Math.min(2.0,
                effectiveFactor * promptTokens / estimatedTokens));
        ratio = observed ? ratio * 0.5 + sample * 0.5 : sample;
        observed = true;
    }
}
