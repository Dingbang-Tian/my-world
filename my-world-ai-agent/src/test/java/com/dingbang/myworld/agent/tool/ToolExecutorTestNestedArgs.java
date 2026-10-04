package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * 不支持的嵌套参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public class ToolExecutorTestNestedArgs {
    /**
     * 嵌套对象。
     */
    @ToolParam(description = "嵌套对象")
    public ToolExecutorTestAddArgs nested;
}
