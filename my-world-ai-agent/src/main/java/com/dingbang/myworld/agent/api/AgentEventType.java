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
    /** 原始历史的完整交换已被摘要覆盖。 */
    MEMORY_COMPRESSED,
    /**
     * 工具准备、调用、完成或失败阶段。
     */
    TOOL_EXECUTION,
    /** 计划已创建。 */
    PLAN_CREATED,
    /** 计划步骤开始。 */
    PLAN_STEP_STARTED,
    /** 计划步骤结束，包含成功或失败状态。 */
    PLAN_STEP_FINISHED,
    /** 计划已结束，包含真实的整体状态。 */
    PLAN_FINISHED,
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
    TIMED_OUT,
    /** 可能存在已发生但结果不确定的外部副作用。 */
    NEEDS_REVIEW
}
