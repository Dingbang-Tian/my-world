package com.dingbang.myworld.agent.persistence;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.Message;
import java.util.List;

/**
 * 保存运行、事件与工具副作用检查点的持久化契约。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public interface RunJournal {
    /** 不启用持久化时的空实现。 */
    RunJournal NONE = new RunJournal() {
        /** {@inheritDoc} */
        @Override public void start(String runId, String sessionId, AgentRequest request,
                                    String modelId, String promptHash, String resumedFromRunId) { }
        /** {@inheritDoc} */
        @Override public void event(AgentEvent event) { }
        /** {@inheritDoc} */
        @Override public void toolStarted(String runId, ToolCall call) { }
        /** {@inheritDoc} */
        @Override public void toolCompleted(String runId, ToolResult result) { }
        /** {@inheritDoc} */
        @Override public void finish(AgentResult result) { }
        /** {@inheritDoc} */
        @Override public void checkpoint(String runId, String sessionId, AgentRequest request,
                                         long sessionVersion, int nextModelTurn, List<Message> exchange) { }
        /** {@inheritDoc} */
        @Override public void artifact(String runId, String operation, String path, String hash) { }
    };

    /**
     * 在模型或工具执行前登记本次运行。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param request 用户请求
     * @param modelId 可信模型标识
     * @param promptHash 模板哈希
     * @param resumedFromRunId 来源运行，可为 null
     */
    void start(String runId, String sessionId, AgentRequest request,
               String modelId, String promptHash, String resumedFromRunId);

    /**
     * 登记包含父子运行关联的执行记录。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param request 用户请求
     * @param modelId 可信模型标识
     * @param promptHash 模板哈希
     * @param resumedFromRunId 恢复来源运行
     * @param parentRunId 父运行标识
     * @param rootRunId 根运行标识
     */
    default void start(String runId, String sessionId, AgentRequest request, String modelId,
                       String promptHash, String resumedFromRunId, String parentRunId, String rootRunId) {
        start(runId, sessionId, request, modelId, promptHash, resumedFromRunId);
    }

    /**
     * 保存单个有序事件。
     *
     * @param event 运行事件
     */
    void event(AgentEvent event);

    /**
     * 在实际工具调用前保存执行意图。
     *
     * @param runId 运行标识
     * @param call 待执行调用
     */
    void toolStarted(String runId, ToolCall call);

    /**
     * 在工具返回后保存确定结果。
     *
     * @param runId 运行标识
     * @param result 工具结果
     */
    void toolCompleted(String runId, ToolResult result);

    /**
     * 保存唯一终态。
     *
     * @param result 最终结果
     */
    void finish(AgentResult result);

    /**
     * 保存当前完整或部分交换以在安全边界继续模型调用。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param request 原用户请求
     * @param sessionVersion 开始运行时的历史版本
     * @param nextModelTurn 恢复时的下一个模型回合编号
     * @param exchange 本轮已确定的消息
     */
    void checkpoint(String runId, String sessionId, AgentRequest request,
                    long sessionVersion, int nextModelTurn, List<Message> exchange);

    /**
     * 保存已发生的代码生成文件副作用记录。
     *
     * @param runId 运行标识
     * @param operation 操作类型
     * @param path 工作目录相对路径
     * @param hash 删除前或写入后的文件哈希
     */
    void artifact(String runId, String operation, String path, String hash);
}
