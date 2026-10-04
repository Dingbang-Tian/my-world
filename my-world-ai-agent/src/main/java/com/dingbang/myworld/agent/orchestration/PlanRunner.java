package com.dingbang.myworld.agent.orchestration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 保存单次计划的步骤状态与真实结果，由 Agent 运行时驱动模型调用。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class PlanRunner {
    /**
     * 不可变计划定义。
     */
    private final Plan plan;
    /**
     * 按步骤保存的程序状态。
     */
    private final List<PlanStepStatus> statuses;
    /**
     * 按步骤保存的实际结果或错误。
     */
    private final List<String> results;
    /**
     * 当前步骤的零基序号。
     */
    private int index = -1;

    /**
     * 为一次运行创建独立计划状态。
     *
     * @param plan 已校验的计划定义
     */
    public PlanRunner(Plan plan) {
        this.plan = Objects.requireNonNull(plan, "计划不能为 null");
        this.statuses = new ArrayList<>(Collections.nCopies(plan.getSteps().size(), PlanStepStatus.PENDING));
        this.results = new ArrayList<>(Collections.nCopies(plan.getSteps().size(), null));
    }

    /**
     * 从数据库中确认完成的步骤状态恢复下一安全边界。
     *
     * @param plan 已校验计划定义
     * @param restoredStatuses 按序保存的步骤状态
     * @param restoredResults 已完成步骤结果
     * @return 可继续执行的计划运行器
     */
    public static PlanRunner restore(Plan plan, List<PlanStepStatus> restoredStatuses,
                                     List<String> restoredResults) {
        Objects.requireNonNull(restoredStatuses, "步骤状态不能为 null");
        Objects.requireNonNull(restoredResults, "步骤结果不能为 null");
        if (restoredStatuses.size() != plan.getSteps().size()
                || restoredResults.size() != plan.getSteps().size()) {
            throw new IllegalArgumentException("恢复计划的步骤数量不匹配");
        }
        // 待恢复的计划运行器。
        PlanRunner runner = new PlanRunner(plan);
        // 是否已经遇到未执行步骤。
        boolean pending = false;
        for (int index = 0; index < restoredStatuses.size(); index++) {
            // 当前步骤状态。
            PlanStepStatus status = restoredStatuses.get(index);
            if (status == PlanStepStatus.RUNNING || (pending && status != PlanStepStatus.PENDING)) {
                throw new IllegalArgumentException("计划未停在可恢复的步骤边界");
            }
            if (status == PlanStepStatus.PENDING) {
                pending = true;
            } else {
                if (status != PlanStepStatus.SUCCEEDED && status != PlanStepStatus.FAILED
                        && status != PlanStepStatus.SKIPPED) {
                    throw new IllegalArgumentException("未知计划步骤状态");
                }
                runner.index = index;
            }
            runner.statuses.set(index, status);
            runner.results.set(index, restoredResults.get(index));
        }
        return runner;
    }

    /**
     * 开始下一步骤。
     *
     * @return 当前步骤；没有后续步骤时为 null
     */
    public PlanStep startNext() {
        if (index >= 0 && statuses.get(index) == PlanStepStatus.RUNNING) {
            throw new IllegalStateException("当前步骤尚未结束");
        }
        if (index + 1 >= statuses.size()) {
            return null;
        }
        index++;
        statuses.set(index, PlanStepStatus.RUNNING);
        return plan.getSteps().get(index);
    }

    /**
     * 记录当前步骤的真实结果；停止策略会跳过未执行步骤。
     *
     * @param status 成功或失败
     * @param result 步骤输出或错误说明
     */
    public void finishCurrent(PlanStepStatus status, String result) {
        if (index < 0 || statuses.get(index) != PlanStepStatus.RUNNING
                || (status != PlanStepStatus.SUCCEEDED && status != PlanStepStatus.FAILED)) {
            throw new IllegalStateException("没有可完成的运行步骤");
        }
        statuses.set(index, status);
        results.set(index, Objects.requireNonNull(result, "步骤结果不能为 null"));
        if (status == PlanStepStatus.FAILED && plan.getFailurePolicy() == PlanFailurePolicy.STOP) {
            // 尚未执行的步骤索引。
            for (int remaining = index + 1; remaining < statuses.size(); remaining++) {
                statuses.set(remaining, PlanStepStatus.SKIPPED);
            }
            index = statuses.size() - 1;
        }
    }

    /**
     * 返回下一步可见的已完成步骤结果。
     *
     * @return 按步骤排序的结果摘要
     */
    public String priorResults() {
        // 前序结果文本。
        StringBuilder summary = new StringBuilder();
        // 前序步骤的索引。
        for (int step = 0; step < index; step++) {
            if (results.get(step) != null) {
                summary.append("步骤 ").append(step + 1).append(" [")
                        .append(statuses.get(step)).append("]: ").append(results.get(step)).append('\n');
            }
        }
        return summary.length() == 0 ? "无" : summary.toString();
    }

    /**
     * 汇总每一步的程序状态与结果。
     *
     * @return 可回传模型的完整计划结果
     */
    public String summary() {
        // 完整计划摘要。
        StringBuilder summary = new StringBuilder("计划 ").append(plan.getName())
                .append("：").append(succeeded() ? "SUCCEEDED" : "FAILED").append('\n');
        // 待汇总步骤的索引。
        for (int step = 0; step < statuses.size(); step++) {
            summary.append("步骤 ").append(step + 1).append(" [")
                    .append(statuses.get(step)).append("] ")
                    .append(plan.getSteps().get(step).getDescription());
            if (results.get(step) != null) {
                summary.append(" → ").append(results.get(step));
            }
            summary.append('\n');
        }
        return summary.toString();
    }

    /**
     * 判断全部步骤是否真实成功。
     *
     * @return 仅全部成功时为 true
     */
    public boolean succeeded() {
        return statuses.stream().allMatch(status -> status == PlanStepStatus.SUCCEEDED);
    }

    /**
     * 返回当前计划定义。
     *
     * @return 不可变计划
     */
    public Plan getPlan() {
        return plan;
    }

    /**
     * 返回当前步骤。
     *
     * @return 当前步骤；尚未启动时为 null
     */
    public PlanStep currentStep() {
        return index < 0 ? null : plan.getSteps().get(index);
    }

    /**
     * 返回程序状态的不可修改快照。
     *
     * @return 步骤状态列表
     */
    public List<PlanStepStatus> statuses() {
        return Collections.unmodifiableList(new ArrayList<>(statuses));
    }
}
