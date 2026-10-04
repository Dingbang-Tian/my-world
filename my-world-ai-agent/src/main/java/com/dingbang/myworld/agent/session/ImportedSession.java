package com.dingbang.myworld.agent.session;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.agent.memory.MemorySummary;
import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * 已解码且等待绑定可信 Agent 定义的会话数据。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class ImportedSession {

    /**
     * 会话标识。
     */
    private final String sessionId;

    /**
     * 所有者标识。
     */
    private final String ownerId;

    /**
     * 应用标识。
     */
    private final String appId;

    /**
     * Agent 标识。
     */
    private final String agentId;

    /**
     * 历史版本。
     */
    private final long version;

    /**
     * 会话模型选项。
     */
    private final ModelOptions options;

    /**
     * 完整历史。
     */
    private final List<Message> messages;
    /**
     * 已提交的摘要，可为 null。
     */
    private final MemorySummary summary;

    /**
     * 固定解码后的会话数据。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param version 历史版本
     * @param options 会话选项
     * @param messages 完整消息
     */
    public ImportedSession(String sessionId, String ownerId, String appId, String agentId,
                           long version, ModelOptions options, List<Message> messages) {
        this(sessionId, ownerId, appId, agentId, version, options, messages, null);
    }

    /**
     * 固定含摘要的已解码会话。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param version 历史版本
     * @param options 会话模型选项
     * @param messages 完整原始消息
     * @param summary 已提交摘要
     */
    public ImportedSession(String sessionId, String ownerId, String appId, String agentId,
                           long version, ModelOptions options, List<Message> messages, MemorySummary summary) {
        this.sessionId = Objects.requireNonNull(sessionId, "会话标识不能为 null");
        this.ownerId = Objects.requireNonNull(ownerId, "所有者标识不能为 null");
        this.appId = Objects.requireNonNull(appId, "应用标识不能为 null");
        this.agentId = Objects.requireNonNull(agentId, "Agent 标识不能为 null");
        this.version = version;
        this.options = Objects.requireNonNull(options, "会话选项不能为 null");
        this.messages = List.copyOf(Objects.requireNonNull(messages, "会话消息不能为 null"));
        this.summary = summary;
    }
}
