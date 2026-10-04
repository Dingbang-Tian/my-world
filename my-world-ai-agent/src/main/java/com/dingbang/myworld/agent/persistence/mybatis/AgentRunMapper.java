package com.dingbang.myworld.agent.persistence.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 保存运行、事件、工具、计划和恢复检查点的 MyBatis-Plus Mapper。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRunRow> {
    /**
     * 按唯一请求键查询运行标识。
     *
     * @param ownerId 所有者
     * @param appId 应用
     * @param requestId 请求标识
     * @return 运行标识；不存在时为空
     */
    @Select("SELECT id FROM agent_run WHERE owner_key=#{ownerId} AND app_id=#{appId} AND request_id=#{requestId}")
    String runIdByRequest(@Param("ownerId") String ownerId, @Param("appId") String appId,
                          @Param("requestId") String requestId);
    /**
     * 保存有序运行事件。
     *
     * @param runId 运行标识
     * @param sequence 事件序号
     * @param type 事件类型
     * @param payloadJson 结构化载荷
     * @param createdAt 事件发生时的 UTC 时间
     * @return 插入行数
     */
    @Insert("INSERT INTO agent_run_event(run_id,seq,type,payload_json,created_at) "
            + "VALUES(#{runId},#{sequence},#{type},#{payloadJson},#{createdAt})")
    int insertEvent(@Param("runId") String runId, @Param("sequence") long sequence,
                    @Param("type") String type, @Param("payloadJson") String payloadJson,
                    @Param("createdAt") java.time.LocalDateTime createdAt);

    /**
     * 保存计划主记录。
     *
     * @param planId 计划标识
     * @param runId 运行标识
     * @param name 计划名称
     * @return 插入行数
     */
    @Insert("INSERT INTO agent_plan(id,run_id,name,status,created_at) "
            + "VALUES(#{planId},#{runId},#{name},'RUNNING',UTC_TIMESTAMP(6))")
    int insertPlan(@Param("planId") String planId, @Param("runId") String runId,
                   @Param("name") String name);

    /**
     * 初始化一个待执行步骤。
     *
     * @param planId 计划标识
     * @param stepIndex 步骤序号
     * @return 插入行数
     */
    @Insert("INSERT INTO agent_plan_step(plan_id,step_index,status,updated_at) "
            + "VALUES(#{planId},#{stepIndex},'PENDING',UTC_TIMESTAMP(6))")
    int insertPlanStep(@Param("planId") String planId, @Param("stepIndex") int stepIndex);

    /**
     * 保存步骤开始或完成状态。
     *
     * @param planId 计划标识
     * @param stepIndex 步骤序号
     * @param state 步骤状态
     * @param result 已完成报告，可为空
     * @return 更新行数
     */
    @Update("UPDATE agent_plan_step SET status=#{state},result=#{result},updated_at=UTC_TIMESTAMP(6) "
            + "WHERE plan_id=#{planId} AND step_index=#{stepIndex}")
    int updatePlanStep(@Param("planId") String planId, @Param("stepIndex") int stepIndex,
                       @Param("state") String state, @Param("result") String result);

    /**
     * 保存整体计划终态。
     *
     * @param planId 计划标识
     * @param state 计划状态
     * @return 更新行数
     */
    @Update("UPDATE agent_plan SET status=#{state},ended_at=UTC_TIMESTAMP(6) WHERE id=#{planId}")
    int finishPlan(@Param("planId") String planId, @Param("state") String state);

    /**
     * 在外部工具调用前保存执行意图。
     *
     * @param id 工具记录标识
     * @param runId 运行标识
     * @param callId 调用标识
     * @param name 工具名
     * @param argsJson 参数 JSON
     * @param argsHash 参数哈希
     * @return 插入行数
     */
    @Insert("INSERT INTO agent_tool_execution(id,run_id,call_id,name,args_json,args_hash,status,started_at) "
            + "VALUES(#{id},#{runId},#{callId},#{name},#{argsJson},#{argsHash},'STARTED',UTC_TIMESTAMP(6))")
    int insertTool(@Param("id") String id, @Param("runId") String runId,
                   @Param("callId") String callId, @Param("name") String name,
                   @Param("argsJson") String argsJson, @Param("argsHash") String argsHash);

    /**
     * 仅将已登记意图转换为确定结果。
     *
     * @param runId 运行标识
     * @param callId 调用标识
     * @param state 结果状态
     * @param resultJson 结果 JSON
     * @return 更新行数
     */
    @Update("UPDATE agent_tool_execution SET status=#{state},result_json=#{resultJson},"
            + "ended_at=UTC_TIMESTAMP(6) WHERE run_id=#{runId} AND call_id=#{callId} AND status='STARTED'")
    int completeTool(@Param("runId") String runId, @Param("callId") String callId,
                     @Param("state") String state, @Param("resultJson") String resultJson);

    /**
     * 只将运行中记录写为唯一终态。
     *
     * @param runId 运行标识
     * @param state 终态
     * @param resultJson 结果 JSON
     * @return 更新行数
     */
    @Update("UPDATE agent_run SET status=#{state},result_json=#{resultJson},ended_at=UTC_TIMESTAMP(6) "
            + "WHERE id=#{runId} AND status='RUNNING'")
    int finishRun(@Param("runId") String runId, @Param("state") String state,
                  @Param("resultJson") String resultJson);

    /**
     * 覆盖一个运行的最新安全检查点。
     *
     * @param runId 运行标识
     * @param sessionVersion 会话历史版本
     * @param nextModelTurn 下一个模型回合
     * @param exchangeJson 部分交换 JSON
     * @return 插入或更新行数
     */
    @Insert("INSERT INTO agent_run_checkpoint(run_id,session_version,next_model_turn,exchange_json,updated_at) "
            + "VALUES(#{runId},#{sessionVersion},#{nextModelTurn},#{exchangeJson},UTC_TIMESTAMP(6)) "
            + "ON DUPLICATE KEY UPDATE session_version=VALUES(session_version),"
            + "next_model_turn=VALUES(next_model_turn),exchange_json=VALUES(exchange_json),"
            + "updated_at=VALUES(updated_at)")
    int upsertCheckpoint(@Param("runId") String runId, @Param("sessionVersion") long sessionVersion,
                         @Param("nextModelTurn") int nextModelTurn,
                         @Param("exchangeJson") String exchangeJson);

    /**
     * 保存文件产物的路径与哈希。
     *
     * @param id 产物标识
     * @param runId 运行标识
     * @param path 相对路径
     * @param operation 操作名称
     * @param beforeHash 删除前哈希
     * @param afterHash 写入后哈希
     * @return 插入行数
     */
    @Insert("INSERT INTO codegen_artifact(id,run_id,path,operation,before_hash,after_hash,created_at) "
            + "VALUES(#{id},#{runId},#{path},#{operation},#{beforeHash},#{afterHash},UTC_TIMESTAMP(6))")
    int insertArtifact(@Param("id") String id, @Param("runId") String runId,
                       @Param("path") String path, @Param("operation") String operation,
                       @Param("beforeHash") String beforeHash, @Param("afterHash") String afterHash);

    /**
     * 按发生顺序读取产物。
     *
     * @param runId 运行标识
     * @return 产物行
     */
    @Select("SELECT path,operation,before_hash AS beforeHash,after_hash AS afterHash "
            + "FROM codegen_artifact WHERE run_id=#{runId} ORDER BY created_at,id")
    List<Map<String, Object>> artifactRows(@Param("runId") String runId);

    /**
     * 读取最新检查点。
     *
     * @param runId 运行标识
     * @return 检查点字段，不存在时为空
     */
    @Select("SELECT session_version AS sessionVersion,next_model_turn AS nextModelTurn,"
            + "exchange_json AS exchangeJson FROM agent_run_checkpoint WHERE run_id=#{runId}")
    Map<String, Object> checkpointRow(@Param("runId") String runId);

    /**
     * 读取已确定的工具结果。
     *
     * @param runId 运行标识
     * @return 调用标识和结果 JSON
     */
    @Select("SELECT call_id AS callId,result_json AS resultJson FROM agent_tool_execution "
            + "WHERE run_id=#{runId} AND status<>'STARTED'")
    List<Map<String, Object>> toolResultRows(@Param("runId") String runId);

    /**
     * 读取尚未完成的计划工具调用。
     *
     * @param runId 运行标识
     * @return 计划调用行
     */
    @Select("SELECT call_id AS callId,args_json AS argsJson FROM agent_tool_execution "
            + "WHERE run_id=#{runId} AND name='create_plan' AND status='STARTED'")
    List<Map<String, Object>> planCallRows(@Param("runId") String runId);

    /**
     * 读取运行中计划的步骤状态与报告。
     *
     * @param runId 运行标识
     * @return 按步骤序号排列的记录
     */
    @Select("SELECT s.status,s.result FROM agent_plan_step s JOIN agent_plan p ON p.id=s.plan_id "
            + "WHERE p.run_id=#{runId} AND p.status='RUNNING' ORDER BY s.step_index")
    List<Map<String, Object>> planStepRows(@Param("runId") String runId);

    /**
     * 查询仍在运行的旧任务。
     *
     * @return 运行标识
     */
    @Select("SELECT id FROM agent_run WHERE status='RUNNING'")
    List<String> runningRunIds();

    /**
     * 查询租约已过期的运行。
     *
     * @param now 当前 UTC 时间
     * @return 运行标识
     */
    @Select("SELECT r.id FROM agent_run r JOIN agent_session s ON s.id=r.session_id "
            + "WHERE r.status='RUNNING' AND (s.lease_until IS NULL OR s.lease_until<#{now})")
    List<String> abandonedRunIds(@Param("now") java.time.LocalDateTime now);

    /**
     * 统计所有结果未知的工具意图。
     *
     * @param runId 运行标识
     * @return 意图数量
     */
    @Select("SELECT COUNT(*) FROM agent_tool_execution WHERE run_id=#{runId} AND status='STARTED'")
    long startedToolCount(@Param("runId") String runId);

    /**
     * 统计计划工具之外的未知意图。
     *
     * @param runId 运行标识
     * @return 意图数量
     */
    @Select("SELECT COUNT(*) FROM agent_tool_execution WHERE run_id=#{runId} "
            + "AND status='STARTED' AND name<>'create_plan'")
    long startedNonPlanToolCount(@Param("runId") String runId);

    /**
     * 将旧运行标为中断或待核查。
     *
     * @param runId 运行标识
     * @param state 新状态
     * @return 更新行数
     */
    @Update("UPDATE agent_run SET status=#{state},ended_at=UTC_TIMESTAMP(6) "
            + "WHERE id=#{runId} AND status='RUNNING'")
    int interruptRun(@Param("runId") String runId, @Param("state") String state);

    /**
     * 读取某运行后续的有界事件页。
     *
     * @param runId 运行标识
     * @param afterSequence 已消费序号
     * @return 最多一千条有序事件
     */
    @Select("SELECT r.session_id AS sessionId,e.seq,e.type,e.payload_json AS payloadJson,"
            + "e.created_at AS createdAt FROM agent_run_event e JOIN agent_run r ON r.id=e.run_id "
            + "WHERE e.run_id=#{runId} AND e.seq>#{afterSequence} ORDER BY e.seq LIMIT 1000")
    List<Map<String, Object>> eventRows(@Param("runId") String runId,
                                         @Param("afterSequence") long afterSequence);

    /**
     * 统计运行是否记录过工具意图。
     *
     * @param runId 运行标识
     * @return 工具数量
     */
    @Select("SELECT COUNT(*) FROM agent_tool_execution WHERE run_id=#{runId}")
    long toolCount(@Param("runId") String runId);
}
