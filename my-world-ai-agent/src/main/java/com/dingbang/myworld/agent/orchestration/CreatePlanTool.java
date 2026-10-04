package com.dingbang.myworld.agent.orchestration;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;


/**
 * 声明计划参数并将计划执行交由当前 Agent 运行时控制。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@ToolInfo(name = "create_plan", description = "创建并顺序执行计划；步骤失败时默认停止，可明确选择继续。")
public final class CreatePlanTool implements Tool<CreatePlanParameters> {
    /**
     * 返回计划参数类型。
     *
     * @return 计划参数类
     */
    @Override
    public Class<CreatePlanParameters> parameterType() {
        return CreatePlanParameters.class;
    }

    /**
     * 拒绝脱离 Agent 运行时直接执行计划。
     *
     * @param parameters 已解析的计划参数
     * @param context 可信执行上下文
     * @return 不会返回
     * @throws IllegalStateException 计划必须由运行时执行时
     */
    @Override
    public ToolExecutionResult execute(CreatePlanParameters parameters, ToolExecutionContext context) {
        throw new IllegalStateException("create_plan 必须由 Agent 运行时执行");
    }

}
