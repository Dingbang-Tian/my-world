package com.dingbang.myworld.agent.persistence.mybatis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 映射 Agent 运行主表的持久化记录。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
@TableName("agent_run")
public class AgentRunRow {
    /**
     * 运行标识。
     */
    @TableId(type = IdType.INPUT)
    private String id;
    /**
     * 会话标识。
     */
    private String sessionId;
    /**
     * 可信所有者。
     */
    private String ownerKey;
    /**
     * 应用标识。
     */
    private String appId;
    /**
     * Agent 标识。
     */
    private String agentId;
    /**
     * 请求幂等标识。
     */
    private String requestId;
    /**
     * 父运行标识。
     */
    private String parentRunId;
    /**
     * 根运行标识。
     */
    private String rootRunId;
    /**
     * 显式恢复的来源运行。
     */
    private String resumedFromRunId;
    /**
     * 运行状态。
     */
    private String status;
    /**
     * 模型标识。
     */
    private String modelId;
    /**
     * 提示词模板哈希。
     */
    private String promptHash;
    /**
     * 不含附件字节的请求 JSON。
     */
    private String requestJson;
    /**
     * 终态结果 JSON。
     */
    private String resultJson;
    /**
     * 开始时间，按 UTC 保存。
     */
    private LocalDateTime startedAt;
    /**
     * 结束时间，按 UTC 保存。
     */
    private LocalDateTime endedAt;
}
