package com.dingbang.myworld.agent.persistence.mybatis;

import com.dingbang.myworld.agent.memory.MemorySummary;
import com.dingbang.myworld.agent.session.Session;
import com.dingbang.myworld.agent.session.SessionExportCodec;
import com.dingbang.myworld.agent.session.SessionRepository;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 通过 MyBatis-Plus Mapper 保存会话、消息和摘要并维护短租约。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class MybatisSessionService implements SessionRepository {
    /**
     * 会话及附属记录 Mapper。
     */
    final AgentSessionMapper sessions;
    /**
     * 包含多表写入的事务模板。
     */
    final TransactionTemplate transactions;
    /**
     * 会话 JSON 契约。
     */
    final SessionExportCodec codec = new SessionExportCodec();
    /**
     * 读取消息 JSON 树的编解码器。
     */
    private final ObjectMapper json = new ObjectMapper();
    /**
     * 只负责续租的守护线程。
     */
    static final ScheduledExecutorService HEARTBEATS = Executors.newSingleThreadScheduledExecutor(task -> {
        // 不阻止 JVM 结束的续租线程。
        Thread thread = new Thread(task, "agent-mysql-lease");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 绑定 Mapper 与数据库事务。
     *
     * @param sessions 会话 Mapper
     * @param transactions 事务模板
     */
    public MybatisSessionService(AgentSessionMapper sessions, TransactionTemplate transactions) {
        this.sessions = Objects.requireNonNull(sessions, "会话 Mapper 不能为空");
        this.transactions = Objects.requireNonNull(transactions, "事务模板不能为空");
    }

    /**
     * 创建空会话。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者
     * @param appId 应用
     * @param agentId Agent
     * @param options 会话选项
     * @return 持久化会话句柄
     */
    @Override
    public Session create(String sessionId, String ownerId, String appId, String agentId, ModelOptions options) {
        return importSession(sessionId, ownerId, appId, agentId, 0, options, List.of(), null);
    }

    /**
     * 查询既有会话。
     *
     * @param sessionId 会话标识
     * @return 会话句柄；不存在时为空
     */
    @Override
    public Session find(String sessionId) {
        return sessions.selectById(sessionId) == null ? null : new PersistentSessionHandle(this, sessionId);
    }

    /**
     * 导入无摘要的完整会话。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者
     * @param appId 应用
     * @param agentId Agent
     * @param version 历史版本
     * @param options 会话选项
     * @param history 完整历史
     * @return 持久化会话句柄
     */
    @Override
    public Session importSession(String sessionId, String ownerId, String appId, String agentId,
                                 long version, ModelOptions options, List<Message> history) {
        return importSession(sessionId, ownerId, appId, agentId, version, options, history, null);
    }

    /**
     * 在同一事务导入会话快照、顺序消息与摘要。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者
     * @param appId 应用
     * @param agentId Agent
     * @param version 历史版本
     * @param options 会话选项
     * @param history 完整历史
     * @param summary 已覆盖摘要，可为空
     * @return 持久化会话句柄
     */
    @Override
    public Session importSession(String sessionId, String ownerId, String appId, String agentId,
                                 long version, ModelOptions options, List<Message> history,
                                 MemorySummary summary) {
        // 经公共会话契约验证的完整 JSON。
        String stateJson = codec.encode(sessionId, ownerId, appId, agentId, options,
                new SessionSnapshot(version, history, summary));
        codec.decode(stateJson);
        try {
            transactions.executeWithoutResult(status -> {
                // 会话主表记录。
                AgentSessionRow row = new AgentSessionRow();
                row.setId(sessionId);
                row.setOwnerKey(ownerId);
                row.setAppId(appId);
                row.setAgentId(agentId);
                row.setVersion(version);
                row.setStateJson(stateJson);
                row.setCreatedAt(now());
                row.setUpdatedAt(now());
                sessions.insert(row);
                insertMessages(sessionId, 0, history, stateJson);
                if (summary != null) insertSummary(sessionId, summary);
            });
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("SESSION_ALREADY_EXISTS", exception);
        }
        return new PersistentSessionHandle(this, sessionId);
    }

    /**
     * 将新增消息按稳定序号写入数据库。
     *
     * @param sessionId 会话标识
     * @param existingCount 当前消息数量
     * @param messages 新消息
     * @param stateJson 包含全部消息的 JSON
     */
    void insertMessages(String sessionId, int existingCount, List<Message> messages, String stateJson) {
        try {
            // 已验证的顺序消息树。
            JsonNode nodes = json.readTree(stateJson).path("messages");
            for (int index = 0; index < messages.size(); index++) {
                // 当前消息。
                Message message = messages.get(index);
                // 来源运行标识，导入历史可为空。
                String messageId = message.getMessageId();
                sessions.insertMessage(messageId, sessionId, existingCount + index + 1L,
                        messageId.contains(":") ? messageId.substring(0, messageId.indexOf(':')) : null,
                        message.getRole().name(), nodes.get(existingCount + index).toString());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("PERSISTENCE_ERROR", exception);
        }
    }

    /**
     * 保存摘要覆盖记录。
     *
     * @param sessionId 会话标识
     * @param summary 新摘要
     */
    void insertSummary(String sessionId, MemorySummary summary) {
        sessions.insertSummary(UUID.randomUUID().toString(), sessionId,
                summary.getCoveredMessageCount(), summary.getText());
    }

    /**
     * 获取用于数据库租约比较的 UTC 时间。
     *
     * @return 当前 UTC 时间
     */
    static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

}
