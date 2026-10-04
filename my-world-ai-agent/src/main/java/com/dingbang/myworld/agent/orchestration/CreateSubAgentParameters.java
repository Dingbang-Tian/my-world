package com.dingbang.myworld.agent.orchestration;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import java.util.List;

/**
 * 模型可填写的子 Agent 委派参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class CreateSubAgentParameters {
    /**
     * 子 Agent 名称。
     */
    @ToolParam(description = "子 Agent 名称")
    public String name;
    /**
     * 子 Agent 职责。
     */
    @ToolParam(description = "子 Agent 的职责描述")
    public String description;
    /**
     * 要独立完成的任务。
     */
    @ToolParam(description = "要交给子 Agent 完成的任务")
    public String task;
    /**
     * 明确选取的父级上下文文本；省略时为空。
     */
    @ToolParam(description = "明确交给子 Agent 的必要上下文，不会自动复制父历史", required = false)
    public String context;
    /**
     * 申请继承的父级工具名称；省略时无工具。
     */
    @ToolParam(description = "父运行已授权工具的子集；省略时不授权任何工具", required = false)
    public List<String> toolIds;
}
