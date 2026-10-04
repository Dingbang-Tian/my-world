package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * 两个整数参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public class ToolExecutorTestAddArgs {
    /**
     * 第一个加数。
     */
    @ToolParam(description = "第一个加数")
    int a;
    /**
     * 第二个加数。
     */
    @ToolParam(description = "第二个加数")
    int b;
}
