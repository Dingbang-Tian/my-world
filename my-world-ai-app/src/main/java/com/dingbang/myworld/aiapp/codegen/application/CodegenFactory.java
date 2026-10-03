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
import com.dingbang.myworld.aiapp.codegen.tool.ExecuteCommandTool;
import com.dingbang.myworld.aiapp.codegen.tool.WorkspacePolicy;
import com.dingbang.myworld.aiframework.api.ModelGateway;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;

/**
 * 为代码生成应用单独组装 Agent、技能、文件与命令工具。
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
        return create(gateway, prompts, modelId, workspace, writeEnabled, false,
                List.of("PATH", "JAVA_HOME", "LANG", "TMPDIR"));
    }

    /**
     * 按可信权限分别授权文件写入和命令执行。
     *
     * @param gateway 单次模型入口
     * @param prompts 已加载的模板仓库
     * @param modelId 模型标识
     * @param workspace 工作目录
     * @param writeEnabled 是否允许文件写入
     * @param commandEnabled 是否允许命令执行
     * @param environmentAllowlist 子进程环境变量白名单
     * @return 代码生成服务
     */
    public CodegenService create(ModelGateway gateway, PromptRepository prompts, String modelId,
                                 Path workspace, boolean writeEnabled, boolean commandEnabled,
                                 List<String> environmentAllowlist) {
        /** 固定工作目录的路径策略。 */
        WorkspacePolicy policy = new WorkspacePolicy(workspace);
        /** 工作目录内的文件工具。 */
        FileTools fileTools = new FileTools(policy);
        /** 完整文件工具集合。 */
        List<Tool<?>> all = fileTools.all();
        /** 只读或含写权限的工具集合。 */
        List<Tool<?>> selected = new ArrayList<>(writeEnabled ? all : all.subList(0, 5));
        /** 当前应用独占的命令工具。 */
        ExecuteCommandTool commandTool = null;
        if (commandEnabled) {
            commandTool = new ExecuteCommandTool(policy, environmentAllowlist);
            selected.add(commandTool);
        }
        /** 文件工具的授权名称。 */
        List<String> fileNames = (writeEnabled ? all : all.subList(0, 5)).stream()
                .map(tool -> tool.descriptor().getName()).toList();
        /** 根据文件权限拼接的技能说明。 */
        String fileInstructions = prompts.get("codegen/skills/files").getContent();
        if (writeEnabled) {
            fileInstructions += "\n" + prompts.get("codegen/skills/write").getContent();
        }
        /** 本应用实际授权的技能列表。 */
        List<AgentSkill> skills = new ArrayList<>();
        skills.add(new AgentSkill("codegen/files", fileInstructions, fileNames));
        if (commandEnabled) {
            skills.add(new AgentSkill("codegen/command",
                    prompts.get("codegen/skills/command").getContent(), List.of("execute_command")));
        }
        /** 当前定义使用的技能标识。 */
        List<String> skillIds = skills.stream().map(AgentSkill::getSkillId).toList();
        /** 只属于代码生成应用的 Agent 定义。 */
        AgentDefinition definition = new AgentDefinition(CodegenService.APP_ID, CodegenService.AGENT_ID,
                "代码生成助手", "分析和修改指定工作目录中的代码", modelId, "codegen/system",
                List.of(), skillIds, AgentLimits.defaults(8));
        /** 运行与工具执行服务。 */
        DefaultAgentService agent = new DefaultAgentService(gateway, prompts, List.of(definition),
                new ToolRegistry(selected), skills, ForkJoinPool.commonPool());
        return new CodegenService(agent, fileTools, commandTool);
    }
}
