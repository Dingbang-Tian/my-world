package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 带执行计数的测试加法工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@ToolInfo(name = "add", description = "计算两个整数之和")
public final class OpenAiChatAgentIntegrationTestAddTool implements Tool<OpenAiChatAgentIntegrationTestAddArgs> {
    /**
     * 已执行的 Java 工具次数。
     */
    final AtomicInteger invocations = new AtomicInteger();

    /**
     * 返回参数类型。
     *
     * @return 加法参数类型
     */
    @Override
    public Class<OpenAiChatAgentIntegrationTestAddArgs> parameterType() {
        return OpenAiChatAgentIntegrationTestAddArgs.class;
    }

    /**
     * 执行整数加法并计数。
     *
     * @param parameters 已校验的工具参数
     * @param context 可信运行上下文
     * @return 整数求和结果
     */
    @Override
    public ToolExecutionResult execute(OpenAiChatAgentIntegrationTestAddArgs parameters, ToolExecutionContext context) {
        invocations.incrementAndGet();
        return ToolExecutionResult.text(Integer.toString(parameters.a + parameters.b));
    }
}
