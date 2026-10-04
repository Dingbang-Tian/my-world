package com.dingbang.myworld.agent.persistence.jdbc;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.agent.memory.MemorySummary;
import com.dingbang.myworld.agent.session.Session;
import com.dingbang.myworld.agent.session.SessionExportCodec;
import com.dingbang.myworld.agent.session.SessionRepository;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 使用 H2/JDBC 保存完整会话、消息、摘要和跨进程短租约。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class JdbcSessionRepository implements SessionRepository {
    /**
     * 数据库连接提供者。
     */
     final AgentJdbcDatabase database;
    /**
     * 会话数据编解码器。
     */
     final SessionExportCodec codec = new SessionExportCodec();
    /**
     * JSON 树读取器。
     */
    private final ObjectMapper mapper = new ObjectMapper();
    /**
     * 租约续期专用守护线程。
     */
     static final ScheduledExecutorService HEARTBEATS = Executors.newSingleThreadScheduledExecutor(task -> {
        // 不阻止进程退出的续租线程。
        Thread thread = new Thread(task, "agent-jdbc-lease");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 绑定已经迁移的数据库。
     *
     * @param database 数据库连接提供者
     */
    public JdbcSessionRepository(AgentJdbcDatabase database) {
        this.database = Objects.requireNonNull(database, "数据库不能为 null");
    }

    /**
     * 创建不可覆盖的空会话。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param options 会话选项
     * @return 新会话
     */
    @Override
    public Session create(String sessionId, String ownerId, String appId, String agentId, ModelOptions options) {
        return importSession(sessionId, ownerId, appId, agentId, 0, options, List.of(), null);
    }

    /**
     * 查询数据库中的会话。
     *
     * @param sessionId 会话标识
     * @return 会话句柄，不存在时为 null
     */
    @Override
    public Session find(String sessionId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("SELECT id FROM agent_session WHERE id=?")) {
            statement.setString(1, sessionId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new JdbcSessionHandle(this, sessionId) : null;
            }
        } catch (SQLException exception) {
            throw persistence(exception);
        }
    }

    /**
     * 导入完整会话但不附带摘要。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param version 历史版本
     * @param options 模型选项
     * @param history 完整消息
     * @return 导入的会话
     */
    @Override
    public Session importSession(String sessionId, String ownerId, String appId, String agentId,
                                 long version, ModelOptions options, List<Message> history) {
        return importSession(sessionId, ownerId, appId, agentId, version, options, history, null);
    }

    /**
     * 在一个事务中导入消息和摘要。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param version 历史版本
     * @param options 模型选项
     * @param history 完整消息
     * @param summary 已提交摘要
     * @return 导入的会话
     */
    @Override
    public Session importSession(String sessionId, String ownerId, String appId, String agentId,
                                 long version, ModelOptions options, List<Message> history, MemorySummary summary) {
        // 先用公共导出契约校验所有值，避免写入部分无效数据。
        String json = codec.encode(sessionId, ownerId, appId, agentId, options,
                new SessionSnapshot(version, history, summary));
        codec.decode(json);
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO agent_session(id,owner_key,app_id,agent_id,version,state_json,created_at,updated_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                    statement.setString(1, sessionId);
                    statement.setString(2, ownerId);
                    statement.setString(3, appId);
                    statement.setString(4, agentId);
                    statement.setLong(5, version);
                    statement.setString(6, json);
                    statement.executeUpdate();
                }
                insertMessages(connection, sessionId, 0, history, json);
                if (summary != null) insertSummary(connection, sessionId, summary);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
            return new JdbcSessionHandle(this, sessionId);
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) throw new IllegalStateException("SESSION_ALREADY_EXISTS", exception);
            throw persistence(exception);
        } catch (Exception exception) {
            throw new IllegalStateException("PERSISTENCE_ERROR", exception);
        }
    }

    /**
     * 按现有 JSON 结构保存单条消息，并以数据库唯一约束保护序号。
     *
     * @param connection 当前事务连接
     * @param sessionId 会话标识
     * @param existingCount 已有消息数
     * @param messages 新消息
     * @param fullJson 完整会话 JSON
     * @throws Exception JSON 或数据库操作失败时
     */
     void insertMessages(Connection connection, String sessionId, int existingCount,
                                List<Message> messages, String fullJson) throws Exception {
        // 已验证的会话消息树。
        JsonNode nodes = mapper.readTree(fullJson).path("messages");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO agent_message(id,session_id,seq,run_id,role,body_json,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)")) {
            // 本次新增消息的索引。
            for (int index = 0; index < messages.size(); index++) {
                // 当前消息。
                Message message = messages.get(index);
                statement.setString(1, message.getMessageId());
                statement.setString(2, sessionId);
                statement.setLong(3, existingCount + index + 1L);
                // 运行标识来自运行时生成的消息 ID，导入历史允许为空。
                String messageId = message.getMessageId();
                statement.setString(4, messageId.contains(":") ? messageId.substring(0, messageId.indexOf(':')) : null);
                statement.setString(5, message.getRole().name());
                statement.setString(6, nodes.get(existingCount + index).toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * 保存新摘要覆盖范围。
     *
     * @param connection 当前事务连接
     * @param sessionId 会话标识
     * @param summary 新摘要
     * @throws SQLException 数据库写入失败时
     */
     static void insertSummary(Connection connection, String sessionId, MemorySummary summary)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO agent_memory_summary(id,session_id,through_message_seq,summary,created_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, sessionId);
            statement.setLong(3, summary.getCoveredMessageCount());
            statement.setString(4, summary.getText());
            statement.executeUpdate();
        }
    }

    /**
     * 将数据库故障转换为可识别的存储错误。
     *
     * @param exception JDBC 错误
     * @return 运行时存储错误
     */
     static IllegalStateException persistence(SQLException exception) {
        return new IllegalStateException("PERSISTENCE_ERROR", exception);
    }

}
