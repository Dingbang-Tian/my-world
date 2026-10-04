package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;

/**
 * 风险工具的模型可填写参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class JdbcPersistenceRecoveryTestRiskyArgs {
    /**
     * 测试内容。
     */
    @ToolParam(description = "测试内容")
    public String content;
}
