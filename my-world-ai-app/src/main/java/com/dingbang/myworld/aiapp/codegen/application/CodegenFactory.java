package com.dingbang.myworld.aiapp.codegen.application;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentLimits;
import com.dingbang.myworld.agent.prompt.PromptRepository;
import com.dingbang.myworld.agent.runtime.DefaultAgentService;
import com.dingbang.myworld.agent.skill.AgentSkill;
import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiapp.codegen.api.CodegenService;
import com.dingbang.myworld.aiapp.codegen.tool.FileTools;
import com.dingbang.myworld.aiapp.codegen.tool.WorkspacePolicy;
import com.dingbang.myworld.aiframework.api.ModelGateway;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * 为代码生成应用单独组装 Agent、技能与文件工具。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class CodegenFactory {
    /**
     * 创建绑定固定工作目录的代码生成服务。
     *
     * @param gateway 单次模型入口
     * @param prompts 已加载的模板仓库
     * @param modelId 已配置的模型标识
     * @param workspace 可信工作目录
     * @param writeEnabled 是否授权写文件工具
     * @return 应用服务
     */
    public CodegenService create(ModelGateway gateway, PromptRepository prompts, String modelId,
                                 Path workspace, boolean writeEnabled) {
        /** 工作目录内的文件工具。 */
        FileTools fileTools = new FileTools(new WorkspacePolicy(workspace));
        /** 完整文件工具集合。 */
        List<Tool<?>> all = fileTools.all();
        /** 只读或含写权限的工具集合。 */
        List<Tool<?>> selected = writeEnabled ? all : all.subList(0, 5);
        /** 当前配置授权的工具名称。 */
        List<String> names = selected.stream().map(tool -> tool.descriptor().getName()).toList();
        /** 文件技能说明。 */
        /** 根据实际授权拼接的技能说明。 */
        String instructions = prompts.get("codegen/skills/files").getContent();
        if (writeEnabled) {
            instructions += "\n" + prompts.get("codegen/skills/write").getContent();
        }
        AgentSkill files = new AgentSkill("codegen/files", instructions, names);
        /** 只属于代码生成应用的 Agent 定义。 */
        AgentDefinition definition = new AgentDefinition(CodegenService.APP_ID, CodegenService.AGENT_ID,
                "代码生成助手", "分析和修改指定工作目录中的代码", modelId, "codegen/system",
                List.of(), List.of(files.getSkillId()), AgentLimits.defaults(8));
        /** 运行与工具执行服务。 */
        DefaultAgentService agent = new DefaultAgentService(gateway, prompts, List.of(definition),
                new ToolRegistry(selected), List.of(files), ForkJoinPool.commonPool());
        return new CodegenService(agent, fileTools);
    }
}
