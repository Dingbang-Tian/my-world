package com.dingbang.myworld.aiframework.api;

/**
 * 单次模型回合的标准化结束原因。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
public enum ModelFinishReason {
    /**
     * 模型正常完成回答。
     */
    STOP,
    /**
     * 模型请求调用工具。
     */
    TOOL_CALLS,
    /**
     * 模型达到输出长度限制。
     */
    LENGTH,
    /**
     * 模型拒绝请求。
     */
    REFUSAL,
    /**
     * 暂未识别的供应商结束原因。
     */
    UNKNOWN
}
