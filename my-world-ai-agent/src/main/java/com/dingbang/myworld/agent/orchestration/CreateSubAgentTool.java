package com.dingbang.myworld.agent.orchestration;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;
import com.dingbang.myworld.agent.tool.annotation.ToolParam;

import java.util.List;

/**
 * 声明子 Agent 参数并将执行交由当前 Agent 运行时控制。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@ToolInfo(name = "create_sub_agent", description = "创建独立上下文的子 Agent；只可指定父运行已授权工具的子集。")
public final class CreateSubAgentTool implements Tool<CreateSubAgentTool.Parameters> {
    /**
     * 返回子 Agent 参数类型。
     *
     * @return 参数类
     */
    @Override
    public Class<Parameters> parameterType() {
        return Parameters.class;
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
    public ToolExecutionResult execute(Parameters parameters, ToolExecutionContext context) {
        throw new IllegalStateException("create_sub_agent 必须由 Agent 运行时执行");
    }

    /**
     * 模型可填写的子 Agent 委派参数。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public static final class Parameters {
        /** 子 Agent 名称。 */
        @ToolParam(description = "子 Agent 名称")
        public String name;
        /** 子 Agent 职责。 */
        @ToolParam(description = "子 Agent 的职责描述")
        public String description;
        /** 要独立完成的任务。 */
        @ToolParam(description = "要交给子 Agent 完成的任务")
        public String task;
        /** 明确选取的父级上下文文本；省略时为空。 */
        @ToolParam(description = "明确交给子 Agent 的必要上下文，不会自动复制父历史", required = false)
        public String context;
        /** 申请继承的父级工具名称；省略时无工具。 */
        @ToolParam(description = "父运行已授权工具的子集；省略时不授权任何工具", required = false)
        public List<String> toolIds;
    }
}
