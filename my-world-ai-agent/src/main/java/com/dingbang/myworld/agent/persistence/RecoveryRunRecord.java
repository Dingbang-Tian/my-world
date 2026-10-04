package com.dingbang.myworld.agent.persistence;

import com.dingbang.myworld.agent.api.AgentResultStatus;
import lombok.Value;

/**
 * 保存恢复决策所需的运行信息。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Value
public class RecoveryRunRecord {
    /**
     * 运行标识。
     */
    String runId;
    /**
     * 会话标识。
     */
    String sessionId;
    /**
     * 所有者标识。
     */
    String ownerId;
    /**
     * 应用标识。
     */
    String appId;
    /**
     * Agent 标识。
     */
    String agentId;
    /**
     * 原请求幂等标识。
     */
    String requestId;
    /**
     * 运行状态。
     */
    AgentResultStatus status;
    /**
     * 原用户输入。
     */
    String userText;
    /**
     * 原请求是否包含附件。
     */
    boolean hasAttachments;
    /**
     * 恢复来源运行标识，可为空。
     */
    String resumedFromRunId;
}
