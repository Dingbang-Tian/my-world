package com.dingbang.myworld.agent.orchestration;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

/**
 * 计划中的不可变单步任务定义。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class PlanStep {
    /**
     * 从一开始的步骤序号。
     */
    private final int number;
    /**
     * 步骤任务说明。
     */
    private final String description;

    /**
     * 创建单步定义。
     *
     * @param number 从一开始的步骤序号
     * @param description 非空步骤说明
     */
    public PlanStep(int number, String description) {
        if (number < 1 || StringUtils.isBlank(description)) {
            throw new IllegalArgumentException("步骤序号与说明必须有效");
        }
        this.number = number;
        this.description = description.trim();
    }
}
