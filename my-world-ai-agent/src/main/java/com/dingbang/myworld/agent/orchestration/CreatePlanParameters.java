package com.dingbang.myworld.agent.orchestration;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import java.util.List;

/**
 * 模型可填写的计划参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class CreatePlanParameters {
    /**
     * 计划名称。
     */
    @ToolParam(description = "计划名称")
    public String name;
    /**
     * 计划目标。
     */
    @ToolParam(description = "计划目标和预期产物")
    public String description;
    /**
     * 按顺序执行的步骤说明。
     */
    @ToolParam(description = "按顺序执行的步骤列表")
    public List<String> steps;
    /**
     * 失败策略；省略时停止。
     */
    @ToolParam(description = "步骤失败策略：STOP 或 CONTINUE；默认 STOP", required = false)
    public PlanFailurePolicy failurePolicy;
}
