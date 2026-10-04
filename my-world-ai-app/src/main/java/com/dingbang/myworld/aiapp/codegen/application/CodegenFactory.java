package com.dingbang.myworld.aiapp.codegen.application;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentLimits;
import com.dingbang.myworld.agent.memory.ContextPolicy;
import com.dingbang.myworld.agent.memory.TokenEstimator;
import com.dingbang.myworld.agent.memory.CalibratedTokenEstimator;
import com.dingbang.myworld.agent.orchestration.CreatePlanTool;
import com.dingbang.myworld.agent.orchestration.CreateSubAgentTool;
import com.dingbang.myworld.agent.prompt.PromptRepository;
import com.dingbang.myworld.agent.persistence.RunJournal;
import com.dingbang.myworld.agent.session.InMemorySessionRepository;
import com.dingbang.myworld.agent.session.SessionRepository;
import com.dingbang.myworld.agent.runtime.DefaultAgentService;
import com.dingbang.myworld.agent.skill.AgentSkill;
import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiapp.codegen.api.CodegenService;
import com.dingbang.myworld.aiapp.codegen.tool.FileTools;
import com.dingbang.myworld.aiapp.codegen.tool.ExecuteCommandTool;
import com.dingbang.myworld.aiapp.codegen.tool.LocalToolOutputStore;
import com.dingbang.myworld.aiapp.codegen.tool.ReadToolOutputTool;
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
        return create(gateway, prompts, modelId, workspace, writeEnabled, commandEnabled,
                environmentAllowlist, false);
    }

    /**
     * 根据可信配置额外授权顺序计划工具。
     *
     * @param gateway 单次模型入口
     * @param prompts 已加载模板仓库
     * @param modelId 模型标识
     * @param workspace 工作目录
     * @param writeEnabled 文件写入权限
     * @param commandEnabled 命令执行权限
     * @param environmentAllowlist 子进程环境变量白名单
     * @param planEnabled 是否授权创建计划
     * @return 代码生成服务
     */
    public CodegenService create(ModelGateway gateway, PromptRepository prompts, String modelId,
                                 Path workspace, boolean writeEnabled, boolean commandEnabled,
                                 List<String> environmentAllowlist, boolean planEnabled) {
        return create(gateway, prompts, modelId, workspace, writeEnabled, commandEnabled,
                environmentAllowlist, planEnabled, false);
    }

    /**
     * 根据可信配置额外授权独立子 Agent 委派。
     *
     * @param gateway 单次模型入口
     * @param prompts 已加载模板仓库
     * @param modelId 模型标识
     * @param workspace 工作目录
     * @param writeEnabled 文件写入权限
     * @param commandEnabled 命令执行权限
     * @param environmentAllowlist 子进程环境变量白名单
     * @param planEnabled 是否授权创建计划
     * @param subAgentEnabled 是否授权创建子 Agent
     * @return 代码生成服务
     */
    public CodegenService create(ModelGateway gateway, PromptRepository prompts, String modelId,
                                 Path workspace, boolean writeEnabled, boolean commandEnabled,
                                 List<String> environmentAllowlist, boolean planEnabled,
                                 boolean subAgentEnabled) {
        return create(gateway, prompts, modelId, workspace, writeEnabled, commandEnabled,
                environmentAllowlist, planEnabled, subAgentEnabled, ContextPolicy.defaults());
    }

    /**
     * 根据可信配置组装代码生成服务及上下文策略。
     *
     * @param gateway 单次模型入口
     * @param prompts 已加载模板仓库
     * @param modelId 模型标识
     * @param workspace 工作目录
     * @param writeEnabled 文件写入权限
     * @param commandEnabled 命令执行权限
     * @param environmentAllowlist 子进程环境变量白名单
     * @param planEnabled 是否授权创建计划
     * @param subAgentEnabled 是否授权创建子 Agent
     * @param contextPolicy 上下文窗口与摘要阈值
     * @return 代码生成服务
     */
    public CodegenService create(ModelGateway gateway, PromptRepository prompts, String modelId,
                                 Path workspace, boolean writeEnabled, boolean commandEnabled,
                                 List<String> environmentAllowlist, boolean planEnabled,
                                 boolean subAgentEnabled, ContextPolicy contextPolicy) {
        return create(gateway, prompts, modelId, workspace, writeEnabled, commandEnabled,
                environmentAllowlist, planEnabled, subAgentEnabled, contextPolicy,
                new InMemorySessionRepository(), RunJournal.NONE);
    }

    /**
     * 使用指定会话仓库和运行日志装配代码生成服务。
     *
     * @param gateway 单次模型入口
     * @param prompts 提示词仓库
     * @param modelId 可信模型标识
     * @param workspace 工作目录
     * @param writeEnabled 文件写入开关
     * @param commandEnabled 命令开关
     * @param environmentAllowlist 子进程环境白名单
     * @param planEnabled 计划开关
     * @param subAgentEnabled 子 Agent 开关
     * @param contextPolicy 上下文策略
     * @param sessions 会话仓库
     * @param journal 运行日志
     * @return 代码生成服务
     */
    public CodegenService create(ModelGateway gateway, PromptRepository prompts, String modelId,
                                 Path workspace, boolean writeEnabled, boolean commandEnabled,
                                 List<String> environmentAllowlist, boolean planEnabled,
                                 boolean subAgentEnabled, ContextPolicy contextPolicy,
                                 SessionRepository sessions, RunJournal journal) {
        return create(gateway, prompts, modelId, workspace, writeEnabled, commandEnabled,
                environmentAllowlist, planEnabled, subAgentEnabled, contextPolicy, sessions, journal,
                Path.of(System.getProperty("java.io.tmpdir"), "my-world-codegen-tool-output"));
    }

    /**
     * 使用明确的本地目录保存可分页回读的命令输出。
     *
     * @param gateway 单次模型入口
     * @param prompts 提示词仓库
     * @param modelId 可信模型标识
     * @param workspace 可信工作目录
     * @param writeEnabled 文件写入权限
     * @param commandEnabled 命令执行权限
     * @param environmentAllowlist 命令环境变量白名单
     * @param planEnabled 计划权限
     * @param subAgentEnabled 子 Agent 权限
     * @param contextPolicy 上下文策略
     * @param sessions 会话仓库
     * @param journal 运行日志
     * @param outputDirectory 外置命令输出目录
     * @return 代码生成服务
     */
    public CodegenService create(ModelGateway gateway, PromptRepository prompts, String modelId,
                                 Path workspace, boolean writeEnabled, boolean commandEnabled,
                                 List<String> environmentAllowlist, boolean planEnabled,
                                 boolean subAgentEnabled, ContextPolicy contextPolicy,
                                 SessionRepository sessions, RunJournal journal, Path outputDirectory) {
        return create(gateway, prompts, modelId, workspace, writeEnabled, commandEnabled,
                environmentAllowlist, planEnabled, subAgentEnabled, contextPolicy, sessions, journal,
                outputDirectory, new CalibratedTokenEstimator());
    }

    /**
     * 同时注入可替换的输入估算器和外置命令日志目录。
     *
     * @param gateway 单次模型入口
     * @param prompts 提示词仓库
     * @param modelId 可信模型标识
     * @param workspace 可信工作目录
     * @param writeEnabled 文件写入权限
     * @param commandEnabled 命令执行权限
     * @param environmentAllowlist 命令环境变量白名单
     * @param planEnabled 计划权限
     * @param subAgentEnabled 子 Agent 权限
     * @param contextPolicy 上下文策略
     * @param sessions 会话仓库
     * @param journal 运行日志
     * @param outputDirectory 外置命令日志目录
     * @param tokenEstimator 模型输入估算器
     * @return 代码生成服务
     */
    public CodegenService create(ModelGateway gateway, PromptRepository prompts, String modelId,
                                 Path workspace, boolean writeEnabled, boolean commandEnabled,
                                 List<String> environmentAllowlist, boolean planEnabled,
                                 boolean subAgentEnabled, ContextPolicy contextPolicy,
                                 SessionRepository sessions, RunJournal journal, Path outputDirectory,
                                 TokenEstimator tokenEstimator) {
        // 固定工作目录的路径策略。
        WorkspacePolicy policy = new WorkspacePolicy(workspace);
        // 工作目录内的文件工具。
        FileTools fileTools = new FileTools(policy, journal);
        // 完整文件工具集合。
        List<Tool<?>> all = fileTools.all();
        // 只读或含写权限的工具集合。
        List<Tool<?>> selected = new ArrayList<>(writeEnabled ? all : all.subList(0, 5));
        // 当前应用独占的命令工具。
        ExecuteCommandTool commandTool = null;
        if (commandEnabled) {
            /** 命令与只读回查工具共享会话隔离的外置存储。 */
            LocalToolOutputStore outputStore = new LocalToolOutputStore(outputDirectory);
            commandTool = new ExecuteCommandTool(policy, environmentAllowlist,
                    java.time.Duration.ofSeconds(60), outputStore);
            selected.add(commandTool);
            selected.add(new ReadToolOutputTool(outputStore));
        }
        if (planEnabled) {
            selected.add(new CreatePlanTool());
        }
        if (subAgentEnabled) {
            selected.add(new CreateSubAgentTool());
        }
        // 文件工具的授权名称。
        List<String> fileNames = (writeEnabled ? all : all.subList(0, 5)).stream()
                .map(tool -> tool.descriptor().getName()).toList();
        // 根据文件权限拼接的技能说明。
        String fileInstructions = prompts.get("codegen/skills/files").getContent();
        if (writeEnabled) {
            fileInstructions += "\n" + prompts.get("codegen/skills/write").getContent();
        }
        // 本应用实际授权的技能列表。
        List<AgentSkill> skills = new ArrayList<>();
        skills.add(new AgentSkill("codegen/files", fileInstructions, fileNames));
        if (commandEnabled) {
            skills.add(new AgentSkill("codegen/command",
                    prompts.get("codegen/skills/command").getContent(),
                    List.of("execute_command", "read_tool_output")));
        }
        if (planEnabled) {
            skills.add(new AgentSkill("codegen/plan", "复杂任务可使用 create_plan 创建顺序计划。"
                    + "每步只执行当前任务，依据前序实际结果继续；计划失败应如实报告。",
                    List.of("create_plan")));
        }
        if (subAgentEnabled) {
            skills.add(new AgentSkill("codegen/sub-agent", "可使用 create_sub_agent 委派独立审查任务。"
                    + "为代码审查只传必要上下文，并在 toolIds 中明确列出已授权的只读文件工具；"
                    + "子 Agent 不自动继承父历史或全部工具。", List.of("create_sub_agent")));
        }
        // 当前定义使用的技能标识。
        List<String> skillIds = skills.stream().map(AgentSkill::getSkillId).toList();
        // 只属于代码生成应用的 Agent 定义。
        AgentDefinition definition = new AgentDefinition(CodegenService.APP_ID, CodegenService.AGENT_ID,
                "代码生成助手", "分析和修改指定工作目录中的代码", modelId, "codegen/system",
                List.of(), skillIds, AgentLimits.defaults(planEnabled || subAgentEnabled ? 24 : 8), contextPolicy);
        // 运行与工具执行服务。
        DefaultAgentService agent = new DefaultAgentService(gateway, prompts, List.of(definition),
                new ToolRegistry(selected), skills, ForkJoinPool.commonPool(), sessions, journal,
                tokenEstimator);
        return new CodegenService(agent, fileTools, commandTool, journal);
    }
}
