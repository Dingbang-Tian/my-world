package com.dingbang.myworld.aiapp.codegen.api;

import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.api.AgentService;
import com.dingbang.myworld.agent.persistence.RunJournal;
import com.dingbang.myworld.agent.persistence.RecoveryJournal;
import com.dingbang.myworld.agent.api.AgentRecoveryService;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiapp.codegen.tool.FileTools;
import com.dingbang.myworld.aiapp.codegen.tool.ExecuteCommandTool;

import java.util.List;
import java.util.Objects;

/**
 * 为代码生成应用固定身份并转发 Agent 请求。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class CodegenService {
    /** 代码生成应用标识。 */
    public static final String APP_ID = "codegen";
    /** 代码生成 Agent 标识。 */
    public static final String AGENT_ID = "codegen";
    /** 应用专用 Agent 服务。 */
    private final AgentService agent;
    /** 本应用的文件工具及产物记录。 */
    private final FileTools files;
    /** 可选的命令工具及执行报告。 */
    private final ExecuteCommandTool commands;
    /** 可选的运行与产物持久化记录器。 */
    private final RunJournal journal;

    /**
     * 绑定只允许代码生成身份的 Agent 服务。
     *
     * @param agent 已配置的 Agent 服务
     * @param files 已绑定工作目录的文件工具
     */
    public CodegenService(AgentService agent, FileTools files) {
        this(agent, files, null);
    }

    /**
     * 绑定文件工具和可选命令工具的代码生成服务。
     *
     * @param agent 已配置的 Agent 服务
     * @param files 已绑定工作目录的文件工具
     * @param commands 已授权的命令工具，关闭能力时为 null
     */
    public CodegenService(AgentService agent, FileTools files, ExecuteCommandTool commands) {
        this(agent, files, commands, RunJournal.NONE);
    }

    /**
     * 绑定文件、命令工具和运行持久化记录器。
     *
     * @param agent 已配置的 Agent 服务
     * @param files 文件工具
     * @param commands 可选命令工具
     * @param journal 运行日志
     */
    public CodegenService(AgentService agent, FileTools files, ExecuteCommandTool commands, RunJournal journal) {
        this.agent = Objects.requireNonNull(agent, "Agent 服务不能为空");
        this.files = Objects.requireNonNull(files, "文件工具不能为空");
        this.commands = commands;
        this.journal = Objects.requireNonNull(journal, "运行日志不能为空");
    }

    /**
     * 查询本进程中一次运行成功的文件产物。
     *
     * @param runId Agent 运行标识
     * @return 产物记录快照
     */
    public List<FileArtifact> artifacts(String runId) {
        return files.artifacts(runId);
    }

    /**
     * 按可信所有者读取跨进程保存的文件产物。
     *
     * @param ownerId 所有者标识
     * @param runId 运行标识
     * @return 已保存产物
     */
    public List<FileArtifact> artifacts(String ownerId, String runId) {
        if (!(journal instanceof RecoveryJournal recoveryJournal)) return files.artifacts(runId);
        return recoveryJournal.artifacts(runId, ownerId, APP_ID).stream()
                .map(record -> new FileArtifact(record.runId(), record.operation(), record.path(),
                        record.afterHash() == null ? record.beforeHash() : record.afterHash()))
                .toList();
    }

    /**
     * 查询一次运行已执行的命令及其有界输出。
     *
     * @param runId Agent 运行标识
     * @return 命令报告快照
     */
    public List<CommandReport> commandReports(String runId) {
        return commands == null ? List.of() : commands.reports(runId);
    }

    /**
     * 准备代码生成任务，供调用方订阅事件和显式执行。
     *
     * @param ownerId 可信调用方的所有者标识
     * @param sessionId 已有会话标识，可为空
     * @param requestId 请求标识
     * @param task 用户任务
     * @return 未执行的运行句柄
     */
    public AgentRun prepare(String ownerId, String sessionId, String requestId, String task) {
        return agent.prepare(new AgentRequest(ownerId, APP_ID, AGENT_ID, sessionId, requestId,
                task, ModelOptions.empty()));
    }

    /**
     * 为代码生成任务显式创建关联原运行的新恢复句柄。
     *
     * @param ownerId 可信所有者
     * @param priorRunId 中断的原运行
     * @param newRequestId 新请求标识
     * @return 未执行的恢复运行
     */
    public AgentRun resume(String ownerId, String priorRunId, String newRequestId) {
        if (!(agent instanceof AgentRecoveryService service)) {
            throw new IllegalStateException("当前 Agent 服务不支持检查点恢复");
        }
        return service.resume(priorRunId, ownerId, APP_ID, newRequestId);
    }

    /**
     * 同步执行代码生成任务。
     *
     * @param ownerId 可信调用方的所有者标识
     * @param sessionId 已有会话标识，可为空
     * @param requestId 请求标识
     * @param task 用户任务
     * @return 最终 Agent 结果
     */
    public AgentResult run(String ownerId, String sessionId, String requestId, String task) {
        return agent.run(new AgentRequest(ownerId, APP_ID, AGENT_ID, sessionId, requestId,
                task, ModelOptions.empty()));
    }
}
