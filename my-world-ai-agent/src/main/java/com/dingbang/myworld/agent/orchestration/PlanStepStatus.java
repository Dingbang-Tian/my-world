package com.dingbang.myworld.agent.orchestration;

/**
 * 程序维护的计划步骤执行状态。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public enum PlanStepStatus {
    /**
     * 尚未开始。
     */
    PENDING,
    /**
     * 正在执行。
     */
    RUNNING,
    /**
     * 已成功完成。
     */
    SUCCEEDED,
    /**
     * 已失败。
     */
    FAILED,
    /**
     * 因停止策略未执行。
     */
    SKIPPED
}
