package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import java.util.List;

/**
 * 显式描述工具的多类型参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public class ToolExecutorTestExplicitArgs {
    /**
     * 标题。
     */
    @ToolParam(description = "标题")
    public String title;
    /**
     * 权重。
     */
    @ToolParam(description = "权重")
    public double weight;
    /**
     * 是否启用。
     */
    @ToolParam(description = "是否启用")
    public boolean enabled;
    /**
     * 整数位置列表。
     */
    @ToolParam(description = "位置")
    public List<Integer> offsets;
}
