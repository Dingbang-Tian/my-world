package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 记录真实执行次数的测试工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@ToolInfo(name = "add", description = "计算两个整数之和")
public class AgentToolLoopTestAddTool implements Tool<AgentToolLoopTestAddArgs> {
    /**
     * 工具真实执行次数。
     */
    final AtomicInteger invocations = new AtomicInteger();

    /**
     * 返回参数类型。
     *
     * @return 加法参数类型
     */
    @Override
    public Class<AgentToolLoopTestAddArgs> parameterType() {
        return AgentToolLoopTestAddArgs.class;
    }

    /**
     * 计算和并计数。
     *
     * @param parameters 已校验的加法参数
     * @param context 本次可信运行上下文
     * @return 整数和
     */
    @Override
    public ToolExecutionResult execute(AgentToolLoopTestAddArgs parameters, ToolExecutionContext context) {
        invocations.incrementAndGet();
        return ToolExecutionResult.text(Integer.toString(parameters.a + parameters.b));
    }
}
