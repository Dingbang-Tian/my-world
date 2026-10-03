package com.dingbang.myworld.agent.orchestration;

import com.dingbang.myworld.aiframework.model.ToolResult;

/**
 * 允许工具向计划运行器声明成功执行后仍未达成步骤目标的结果。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public interface PlanStepOutcome {
    /**
     * 根据本工具生成的结果判断步骤是否失败。
     *
     * @param result 本工具的完整结果
     * @return 当前步骤应标记为失败时返回 true
     */
    boolean stepFailed(ToolResult result);
}
