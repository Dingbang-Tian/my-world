package com.dingbang.myworld.agent.persistence.mybatis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 映射 Agent 会话主表的持久化记录。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Getter
@Setter
@TableName("agent_session")
public class AgentSessionRow {
    /** 会话标识。 */
    @TableId(type = IdType.INPUT)
    private String id;
    /** 可信所有者。 */
    private String ownerKey;
    /** 应用标识。 */
    private String appId;
    /** Agent 标识。 */
    private String agentId;
    /** 历史版本。 */
    private Long version;
    /** 完整会话快照 JSON。 */
    private String stateJson;
    /** 当前租约所有者。 */
    private String leaseOwner;
    /** 当前租约到期时间，按 UTC 保存。 */
    private LocalDateTime leaseUntil;
    /** 创建时间，按 UTC 保存。 */
    private LocalDateTime createdAt;
    /** 最近更新时间，按 UTC 保存。 */
    private LocalDateTime updatedAt;
}
