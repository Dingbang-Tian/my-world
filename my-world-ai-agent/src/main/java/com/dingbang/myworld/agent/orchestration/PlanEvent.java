package com.dingbang.myworld.agent.orchestration;

import lombok.Data;

import java.util.Objects;

/**
 * 与计划或单个步骤关联的生命周期事件内容。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class PlanEvent {
    /**
     * 计划名称。
     */
    private final String planName;
    /**
     * 当前步骤序号；计划整体事件为零。
     */
    private final int stepNumber;
    /**
     * 总步骤数。
     */
    private final int stepCount;
    /**
     * 当前步骤状态；计划整体事件为 null。
     */
    private final PlanStepStatus status;
    /**
     * 步骤说明或计划结果。
     */
    private final String detail;

    /**
     * 固定计划事件内容。
     *
     * @param planName 计划名称
     * @param stepNumber 步骤序号；整体事件为零
     * @param stepCount 总步骤数
     * @param status 步骤状态；整体事件为 null
     * @param detail 步骤说明或结果
     */
    public PlanEvent(String planName, int stepNumber, int stepCount, PlanStepStatus status, String detail) {
        this.planName = Objects.requireNonNull(planName, "计划名称不能为 null");
        this.stepNumber = stepNumber;
        this.stepCount = stepCount;
        this.status = status;
        this.detail = Objects.requireNonNull(detail, "事件内容不能为 null");
    }
}
