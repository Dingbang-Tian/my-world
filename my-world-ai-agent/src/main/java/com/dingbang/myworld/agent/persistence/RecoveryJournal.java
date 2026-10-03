package com.dingbang.myworld.agent.persistence;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.orchestration.PlanStepStatus;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.ToolCall;

import java.util.List;

/**
 * 提供运行归属查询、事件回放和显式恢复所需的持久化读取能力。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public interface RecoveryJournal extends RunJournal {
    /**
     * 按归属查询运行。
     *
     * @param runId 运行标识
     * @param ownerId 所有者
     * @param appId 应用
     * @return 运行记录；不存在时为空
     */
    RunRecord find(String runId, String ownerId, String appId);

    /**
     * 读取已确定的工具结果检查点。
     *
     * @param runId 运行标识
     * @return 可恢复检查点；不存在时为空
     */
    RecoveryCheckpoint recoveryCheckpoint(String runId);

    /**
     * 读取安全步骤边界的计划状态。
     *
     * @param runId 运行标识
     * @return 可恢复计划；不存在时为空
     */
    PlanRecovery planRecovery(String runId);

    /**
     * 判断运行中是否登记过工具意图。
     *
     * @param runId 运行标识
     * @return 有工具意图时为真
     */
    boolean hasToolExecutions(String runId);

    /**
     * 按归属读取文件产物。
     *
     * @param runId 运行标识
     * @param ownerId 所有者
     * @param appId 应用
     * @return 产物记录
     */
    List<ArtifactRecord> artifacts(String runId, String ownerId, String appId);

    /**
     * 按归属读取最终结果。
     *
     * @param runId 运行标识
     * @param ownerId 所有者
     * @param appId 应用
     * @return 终态结果；尚未结束时为空
     */
    AgentResult result(String runId, String ownerId, String appId);

    /**
     * 分页读取有序事件。
     *
     * @param runId 运行标识
     * @param ownerId 所有者
     * @param appId 应用
     * @param afterSequence 已消费序号
     * @return 最多一千条事件
     */
    List<AgentEvent> events(String runId, String ownerId, String appId, long afterSequence);

    /**
     * 单实例启动时中断遗留运行。
     *
     * @return 被中断的运行标识
     */
    List<String> interruptOnStartup();

    /**
     * 标记租约失效的运行。
     *
     * @return 被中断的运行标识
     */
    List<String> interruptAbandoned();

    /**
     * 保存恢复决策所需的运行信息。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param ownerId 所有者
     * @param appId 应用
     * @param agentId Agent
     * @param requestId 原请求标识
     * @param status 运行状态
     * @param userText 原用户输入
     * @param hasAttachments 原请求是否含附件
     * @param resumedFromRunId 来源运行
     * @author Sebastian
     * @since 2026/10/03
     */
    record RunRecord(String runId, String sessionId, String ownerId, String appId,
                     String agentId, String requestId, AgentResultStatus status,
                     String userText, boolean hasAttachments, String resumedFromRunId) { }

    /**
     * 保存可继续模型调用的已确认消息边界。
     *
     * @param sessionVersion 开始时的会话版本
     * @param nextModelTurn 下一模型回合编号
     * @param exchange 已确认的部分交换
     * @author Sebastian
     * @since 2026/10/03
     */
    record RecoveryCheckpoint(long sessionVersion, int nextModelTurn, List<Message> exchange) { }

    /**
     * 保存可复用的计划步骤状态。
     *
     * @param call 原计划工具调用
     * @param statuses 按序保存的步骤状态
     * @param results 按序保存的步骤结果
     * @author Sebastian
     * @since 2026/10/03
     */
    record PlanRecovery(ToolCall call, List<PlanStepStatus> statuses, List<String> results) { }

    /**
     * 保存不包含文件内容的产物元数据。
     *
     * @param runId 运行标识
     * @param operation 操作类型
     * @param path 相对路径
     * @param beforeHash 删除前哈希
     * @param afterHash 写入后哈希
     * @author Sebastian
     * @since 2026/10/03
     */
    record ArtifactRecord(String runId, String operation, String path,
                          String beforeHash, String afterHash) { }
}
