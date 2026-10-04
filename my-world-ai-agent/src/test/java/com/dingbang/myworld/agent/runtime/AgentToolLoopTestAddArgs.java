package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * 测试用加法参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public class AgentToolLoopTestAddArgs {
    /**
     * 第一个加数。
     */
    @ToolParam(description = "第一个加数")
    public int a;
    /**
     * 第二个加数。
     */
    @ToolParam(description = "第二个加数")
    public int b;
}
