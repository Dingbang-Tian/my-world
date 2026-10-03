package com.dingbang.myworld.agent.persistence.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 执行会话、顺序消息、摘要和租约的原子数据库操作。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSessionRow> {
    /**
     * 插入按会话内序号排列的消息。
     *
     * @param id 消息标识
     * @param sessionId 会话标识
     * @param sequence 消息序号
     * @param runId 来源运行，可为空
     * @param role 消息角色
     * @param bodyJson 消息 JSON
     * @return 插入行数
     */
    @Insert("INSERT INTO agent_message(id,session_id,seq,run_id,role,body_json,created_at) "
            + "VALUES(#{id},#{sessionId},#{sequence},#{runId},#{role},#{bodyJson},UTC_TIMESTAMP(6))")
    int insertMessage(@Param("id") String id, @Param("sessionId") String sessionId,
                      @Param("sequence") long sequence, @Param("runId") String runId,
                      @Param("role") String role, @Param("bodyJson") String bodyJson);

    /**
     * 插入不可回退的摘要覆盖记录。
     *
     * @param id 摘要标识
     * @param sessionId 会话标识
     * @param throughSequence 已覆盖消息数
     * @param summary 摘要文本
     * @return 插入行数
     */
    @Insert("INSERT INTO agent_memory_summary(id,session_id,through_message_seq,summary,created_at) "
            + "VALUES(#{id},#{sessionId},#{throughSequence},#{summary},UTC_TIMESTAMP(6))")
    int insertSummary(@Param("id") String id, @Param("sessionId") String sessionId,
                      @Param("throughSequence") long throughSequence, @Param("summary") String summary);

    /**
     * 以条件更新取得短租约。
     *
     * @param sessionId 会话标识
     * @param leaseOwner 本句柄标识
     * @param now 当前 UTC 时间
     * @param until 到期 UTC 时间
     * @return 更新行数
     */
    @Update("UPDATE agent_session SET lease_owner=#{leaseOwner},lease_until=#{until} WHERE id=#{sessionId} "
            + "AND (lease_until IS NULL OR lease_until<#{now})")
    int acquireLease(@Param("sessionId") String sessionId, @Param("leaseOwner") String leaseOwner,
                     @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    /**
     * 只为同一持有者续租。
     *
     * @param sessionId 会话标识
     * @param leaseOwner 租约持有者
     * @param until 新到期时间
     * @return 更新行数
     */
    @Update("UPDATE agent_session SET lease_until=#{until} WHERE id=#{sessionId} AND lease_owner=#{leaseOwner}")
    int renewLease(@Param("sessionId") String sessionId, @Param("leaseOwner") String leaseOwner,
                   @Param("until") LocalDateTime until);

    /**
     * 只释放本句柄持有的租约。
     *
     * @param sessionId 会话标识
     * @param leaseOwner 租约持有者
     * @return 更新行数
     */
    @Update("UPDATE agent_session SET lease_owner=NULL,lease_until=NULL WHERE id=#{sessionId} "
            + "AND lease_owner=#{leaseOwner}")
    int releaseLease(@Param("sessionId") String sessionId, @Param("leaseOwner") String leaseOwner);

    /**
     * 在版本及有效租约同时匹配时提交完整交换。
     *
     * @param sessionId 会话标识
     * @param expectedVersion 原版本
     * @param leaseOwner 租约持有者
     * @param now 当前 UTC 时间
     * @param stateJson 新状态 JSON
     * @return 更新行数
     */
    @Update("UPDATE agent_session SET version=version+1,state_json=#{stateJson},updated_at=#{now} "
            + "WHERE id=#{sessionId} AND version=#{expectedVersion} AND lease_owner=#{leaseOwner} "
            + "AND lease_until>#{now}")
    int appendState(@Param("sessionId") String sessionId, @Param("expectedVersion") long expectedVersion,
                    @Param("leaseOwner") String leaseOwner, @Param("now") LocalDateTime now,
                    @Param("stateJson") String stateJson);

    /**
     * 在版本及有效租约同时匹配时更新摘要快照。
     *
     * @param sessionId 会话标识
     * @param expectedVersion 原版本
     * @param leaseOwner 租约持有者
     * @param now 当前 UTC 时间
     * @param stateJson 新状态 JSON
     * @return 更新行数
     */
    @Update("UPDATE agent_session SET state_json=#{stateJson},updated_at=#{now} WHERE id=#{sessionId} "
            + "AND version=#{expectedVersion} AND lease_owner=#{leaseOwner} AND lease_until>#{now}")
    int updateSummaryState(@Param("sessionId") String sessionId,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("leaseOwner") String leaseOwner, @Param("now") LocalDateTime now,
                           @Param("stateJson") String stateJson);

    /**
     * 统计仍有效的会话租约。
     *
     * @param sessionId 会话标识
     * @param now 当前 UTC 时间
     * @return 有效租约数量
     */
    @Select("SELECT COUNT(*) FROM agent_session WHERE id=#{sessionId} AND lease_until>#{now}")
    long busyCount(@Param("sessionId") String sessionId, @Param("now") LocalDateTime now);

    /**
     * 统计本句柄持有的有效租约。
     *
     * @param sessionId 会话标识
     * @param leaseOwner 租约持有者
     * @param now 当前 UTC 时间
     * @return 有效租约数量
     */
    @Select("SELECT COUNT(*) FROM agent_session WHERE id=#{sessionId} AND lease_owner=#{leaseOwner} "
            + "AND lease_until>#{now}")
    long activeLeaseCount(@Param("sessionId") String sessionId,
                          @Param("leaseOwner") String leaseOwner, @Param("now") LocalDateTime now);

    /**
     * 启动扫描解除旧运行的会话租约。
     *
     * @param runId 旧运行标识
     * @return 更新行数
     */
    @Update("UPDATE agent_session SET lease_owner=NULL,lease_until=NULL "
            + "WHERE id=(SELECT session_id FROM agent_run WHERE id=#{runId})")
    int releaseRunLease(@Param("runId") String runId);
}
