package com.dingbang.myworld.agent.api;

/**
 * 从已持久化检查点显式创建新运行的公共恢复接口。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public interface AgentRecoveryService {
    /**
     * 校验归属和恢复边界后创建新的未执行运行。
     *
     * @param priorRunId 原运行标识
     * @param ownerId 可信所有者
     * @param appId 应用标识
     * @param newRequestId 新请求幂等标识
     * @return 新运行句柄
     */
    AgentRun resume(String priorRunId, String ownerId, String appId, String newRequestId);
}
