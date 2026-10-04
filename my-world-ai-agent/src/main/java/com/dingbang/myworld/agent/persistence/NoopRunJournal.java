package com.dingbang.myworld.agent.persistence;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.Message;
import java.util.List;

/**
 * 不保存运行事件的空日志实现。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class NoopRunJournal implements RunJournal {
    /**
     * {@inheritDoc}
     */
    @Override
    public void start(String runId, String sessionId, AgentRequest request,
                                String modelId, String promptHash, String resumedFromRunId) { }
    /**
     * {@inheritDoc}
     */
    @Override
    public void event(AgentEvent event) { }
    /**
     * {@inheritDoc}
     */
    @Override
    public void toolStarted(String runId, ToolCall call) { }
    /**
     * {@inheritDoc}
     */
    @Override
    public void toolCompleted(String runId, ToolResult result) { }
    /**
     * {@inheritDoc}
     */
    @Override
    public void finish(AgentResult result) { }
    /**
     * {@inheritDoc}
     */
    @Override
    public void checkpoint(String runId, String sessionId, AgentRequest request,
                                     long sessionVersion, int nextModelTurn, List<Message> exchange) { }
    /**
     * {@inheritDoc}
     */
    @Override
    public void artifact(String runId, String operation, String path, String hash) { }
}
