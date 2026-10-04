package com.dingbang.myworld.agent.orchestration;


/**
 * 步骤失败后的程序控制策略。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public enum PlanFailurePolicy {
    /**
     * 首次失败即跳过余下步骤。
     */
    STOP,
    /**
     * 记录失败并继续执行余下步骤。
     */
    CONTINUE
}
