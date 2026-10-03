package com.dingbang.myworld.agent.orchestration;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 模型提交并经程序校验的不可变顺序计划。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class Plan {
    /** 计划名称。 */
    private final String name;
    /** 计划目标说明。 */
    private final String description;
    /** 按顺序执行的步骤。 */
    private final List<PlanStep> steps;
    /** 步骤失败后的执行策略。 */
    private final FailurePolicy failurePolicy;

    /**
     * 校验并冻结计划定义。
     *
     * @param name 计划名称
     * @param description 计划目标
     * @param descriptions 按顺序排列的步骤说明
     * @param failurePolicy 失败策略
     * @param maxSteps 可信步骤上限
     */
    public Plan(String name, String description, List<String> descriptions,
                FailurePolicy failurePolicy, int maxSteps) {
        if (StringUtils.isBlank(name) || StringUtils.isBlank(description)
                || descriptions == null || descriptions.isEmpty() || descriptions.size() > maxSteps) {
            throw new IllegalArgumentException("计划名称、目标或步骤数量无效；最多 " + maxSteps + " 步");
        }
        this.name = name.trim();
        this.description = description.trim();
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "失败策略不能为 null");
        /** 已校验的步骤列表。 */
        List<PlanStep> validated = new ArrayList<>();
        /** 当前待建立的步骤索引。 */
        for (int index = 0; index < descriptions.size(); index++) {
            validated.add(new PlanStep(index + 1, descriptions.get(index)));
        }
        this.steps = Collections.unmodifiableList(validated);
    }

    /**
     * 步骤失败后的程序控制策略。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public enum FailurePolicy {
        /** 首次失败即跳过余下步骤。 */
        STOP,
        /** 记录失败并继续执行余下步骤。 */
        CONTINUE
    }
}
