package com.dingbang.myworld.agent.api;

/**
 * 当前阶段可观察的 Agent 运行事件种类。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public enum AgentEventType {
    /**
     * 模型产生文本增量。
     */
    TEXT_DELTA,
    /** 模型提供的推理文本增量。 */
    REASONING_DELTA,
    /** 单次模型调用的最终 token 用量。 */
    USAGE,
    /**
     * 工具准备、调用、完成或失败阶段。
     */
    TOOL_EXECUTION,
    /**
     * Agent 正常完成。
     */
    COMPLETED,
    /**
     * Agent 运行失败。
     */
    FAILED,
    /**
     * 模型回合数达到限制。
     */
    LIMIT_EXCEEDED,
    /** 用户取消运行。 */
    CANCELLED,
    /** 全局截止时间已到。 */
    TIMED_OUT
}
