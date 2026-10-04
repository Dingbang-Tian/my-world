package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import java.util.List;

/**
 * 测试列表与枚举的参数。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public class ToolExecutorTestFilterArgs {
    /**
     * 待处理标签。
     */
    @ToolParam(description = "标签")
    public List<String> labels;
    /**
     * 执行模式。
     */
    @ToolParam(description = "模式")
    public ToolExecutorTestMode mode;
}
