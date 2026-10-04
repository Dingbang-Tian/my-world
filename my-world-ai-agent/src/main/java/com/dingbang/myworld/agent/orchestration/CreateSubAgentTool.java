package com.dingbang.myworld.agent.orchestration;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;


/**
 * 声明子 Agent 参数并将执行交由当前 Agent 运行时控制。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@ToolInfo(name = "create_sub_agent", description = "创建独立上下文的子 Agent；只可指定父运行已授权工具的子集。")
public final class CreateSubAgentTool implements Tool<CreateSubAgentParameters> {
    /**
     * 返回子 Agent 参数类型。
     *
     * @return 参数类
     */
    @Override
    public Class<CreateSubAgentParameters> parameterType() {
        return CreateSubAgentParameters.class;
    }

    /**
     * 拒绝绕过 Agent 运行时直接创建子运行。
     *
     * @param parameters 已解析参数
     * @param context 可信执行上下文
     * @return 不会返回
     * @throws IllegalStateException 子 Agent 必须由运行时执行时
     */
    @Override
    public ToolExecutionResult execute(CreateSubAgentParameters parameters, ToolExecutionContext context) {
        throw new IllegalStateException("create_sub_agent 必须由 Agent 运行时执行");
    }

}
