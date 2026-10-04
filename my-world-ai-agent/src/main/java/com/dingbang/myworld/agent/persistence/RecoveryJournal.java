package com.dingbang.myworld.agent.persistence;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentResult;

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
    RecoveryRunRecord find(String runId, String ownerId, String appId);

    /**
     * 按归属和请求幂等标识查询运行。
     *
     * @param ownerId 所有者
     * @param appId 应用
     * @param requestId 请求幂等标识
     * @return 已登记运行；不存在时为空
     */
    default RecoveryRunRecord findByRequestId(String ownerId, String appId, String requestId) {
        return null;
    }

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
    List<RecoveryArtifactRecord> artifacts(String runId, String ownerId, String appId);

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




}
