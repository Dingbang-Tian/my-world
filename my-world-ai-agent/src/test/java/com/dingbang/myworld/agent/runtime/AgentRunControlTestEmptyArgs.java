package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * 记录执行并可等待取消的工具参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class AgentRunControlTestEmptyArgs {
    /**
     * 无参数工具的占位字段。
     */
    @ToolParam(description = "占位文本")
    public String unused;
}
