package com.dingbang.myworld.agent.persistence.jdbc;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.agent.memory.MemorySummary;
import com.dingbang.myworld.agent.session.ImportedSession;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 使用 H2/JDBC 保存完整会话、消息、摘要和跨进程短租约。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class JdbcSessionRepository implements SessionRepository {
    /** 数据库连接提供者。 */
    private final AgentJdbcDatabase database;
    /** 会话数据编解码器。 */
    private final SessionExportCodec codec = new SessionExportCodec();
    /** JSON 树读取器。 */
    private final ObjectMapper mapper = new ObjectMapper();
    /** 租约续期专用守护线程。 */
    private static final ScheduledExecutorService HEARTBEATS = Executors.newSingleThreadScheduledExecutor(task -> {
        /** 不阻止进程退出的续租线程。 */
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
                return rows.next() ? new JdbcSession(sessionId) : null;
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
        /** 先用公共导出契约校验所有值，避免写入部分无效数据。 */
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
            return new JdbcSession(sessionId);
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
    private void insertMessages(Connection connection, String sessionId, int existingCount,
                                List<Message> messages, String fullJson) throws Exception {
        /** 已验证的会话消息树。 */
        JsonNode nodes = mapper.readTree(fullJson).path("messages");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO agent_message(id,session_id,seq,run_id,role,body_json,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)")) {
            /** 本次新增消息的索引。 */
            for (int index = 0; index < messages.size(); index++) {
                /** 当前消息。 */
                Message message = messages.get(index);
                statement.setString(1, message.getMessageId());
                statement.setString(2, sessionId);
                statement.setLong(3, existingCount + index + 1L);
                /** 运行标识来自运行时生成的消息 ID，导入历史允许为空。 */
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
    private static void insertSummary(Connection connection, String sessionId, MemorySummary summary)
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
    private static IllegalStateException persistence(SQLException exception) {
        return new IllegalStateException("PERSISTENCE_ERROR", exception);
    }

    /**
     * 按会话 ID 以短连接实现独占租约和版本化提交。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    private final class JdbcSession implements Session {
        /** 会话标识。 */
        private final String sessionId;
        /** 当前句柄的唯一租约持有者。 */
        private final String leaseOwner = UUID.randomUUID().toString();
        /** 租约续期任务。 */
        private ScheduledFuture<?> renewal;

        /**
         * 创建轻量级会话句柄。
         *
         * @param sessionId 会话标识
         */
        private JdbcSession(String sessionId) {
            this.sessionId = sessionId;
        }

        /**
         * 校验可信调用方的三重归属。
         *
         * @param ownerId 所有者
         * @param appId 应用
         * @param agentId Agent
         */
        @Override
        public void requireOwner(String ownerId, String appId, String agentId) {
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT owner_key,app_id,agent_id FROM agent_session WHERE id=?")) {
                statement.setString(1, sessionId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || !rows.getString(1).equals(ownerId)
                            || !rows.getString(2).equals(appId) || !rows.getString(3).equals(agentId)) {
                        throw new IllegalArgumentException("会话归属不匹配");
                    }
                }
            } catch (SQLException exception) {
                throw persistence(exception);
            }
        }

        /**
         * 以数据库条件更新取得一分钟租约并安排续期。
         *
         * @return 成功取得时为 true
         */
        @Override
        public synchronized boolean tryStart() {
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE agent_session SET lease_owner=?,lease_until=DATEADD('SECOND',60,CURRENT_TIMESTAMP) WHERE id=? AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)")) {
                statement.setString(1, leaseOwner);
                statement.setString(2, sessionId);
                if (statement.executeUpdate() == 0) return false;
                renewal = HEARTBEATS.scheduleAtFixedRate(this::renewLease, 20, 20, TimeUnit.SECONDS);
                return true;
            } catch (SQLException exception) {
                throw persistence(exception);
            }
        }

        /**
         * 仅为仍由本句柄持有的租约延长有效期。
         */
        private void renewLease() {
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE agent_session SET lease_until=DATEADD('SECOND',60,CURRENT_TIMESTAMP) WHERE id=? AND lease_owner=?")) {
                statement.setString(1, sessionId);
                statement.setString(2, leaseOwner);
                statement.executeUpdate();
            } catch (SQLException ignored) {
                // 下次续期仍会尝试；提交时的租约条件会防止失去租约的运行写入。
            }
        }

        /**
         * 取消续期并仅释放本句柄持有的租约。
         */
        @Override
        public synchronized void release() {
            if (renewal != null) renewal.cancel(false);
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE agent_session SET lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=?")) {
                statement.setString(1, sessionId);
                statement.setString(2, leaseOwner);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw persistence(exception);
            }
        }

        /**
         * 从单行已提交状态读取历史与摘要。
         *
         * @return 一致的会话快照
         */
        @Override
        public SessionSnapshot snapshot() {
            /** 当前已提交的会话。 */
            ImportedSession stored = read();
            return new SessionSnapshot(stored.getVersion(), stored.getMessages(), stored.getSummary());
        }

        /**
         * 使用版本与租约条件原子提交完整交换和顺序消息。
         *
         * @param expectedVersion 原历史版本
         * @param messages 本轮完整消息
         */
        @Override
        public void appendExchange(long expectedVersion, List<Message> messages) {
            Objects.requireNonNull(messages, "消息不能为 null");
            try (Connection connection = database.open()) {
                connection.setAutoCommit(false);
                try {
                    /** 当前状态。 */
                    ImportedSession stored = read(connection);
                    if (stored.getVersion() != expectedVersion) throw new IllegalStateException("VERSION_CONFLICT");
                    /** 添加完整交换后的历史。 */
                    List<Message> history = new ArrayList<>(stored.getMessages());
                    history.addAll(messages);
                    /** 编解码同时校验消息配对和摘要边界。 */
                    String json = codec.encode(sessionId, stored.getOwnerId(), stored.getAppId(),
                            stored.getAgentId(), stored.getOptions(),
                            new SessionSnapshot(expectedVersion + 1, history, stored.getSummary()));
                    codec.decode(json);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE agent_session SET version=?,state_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=? AND lease_owner=? AND lease_until>CURRENT_TIMESTAMP")) {
                        statement.setLong(1, expectedVersion + 1);
                        statement.setString(2, json);
                        statement.setString(3, sessionId);
                        statement.setLong(4, expectedVersion);
                        statement.setString(5, leaseOwner);
                        if (statement.executeUpdate() != 1) throw new IllegalStateException("VERSION_CONFLICT");
                    }
                    insertMessages(connection, sessionId, stored.getMessages().size(), messages, json);
                    connection.commit();
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (SQLException exception) {
                throw persistence(exception);
            } catch (Exception exception) {
                if (exception instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("PERSISTENCE_ERROR", exception);
            }
        }

        /**
         * 在相同版本和租约下前移摘要覆盖位置。
         *
         * @param expectedVersion 原历史版本
         * @param expectedCoveredMessageCount 原覆盖消息数
         * @param summary 新摘要
         */
        @Override
        public void updateSummary(long expectedVersion, int expectedCoveredMessageCount, MemorySummary summary) {
            try (Connection connection = database.open()) {
                connection.setAutoCommit(false);
                try {
                    /** 已提交会话。 */
                    ImportedSession stored = read(connection);
                    if (stored.getVersion() != expectedVersion
                            || (stored.getSummary() == null ? 0 : stored.getSummary().getCoveredMessageCount())
                            != expectedCoveredMessageCount
                            || summary.getCoveredMessageCount() <= expectedCoveredMessageCount) {
                        throw new IllegalStateException("VERSION_CONFLICT");
                    }
                    /** 包含新摘要的序列化状态。 */
                    String json = codec.encode(sessionId, stored.getOwnerId(), stored.getAppId(),
                            stored.getAgentId(), stored.getOptions(),
                            new SessionSnapshot(expectedVersion, stored.getMessages(), summary));
                    codec.decode(json);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE agent_session SET state_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=? AND lease_owner=? AND lease_until>CURRENT_TIMESTAMP")) {
                        statement.setString(1, json);
                        statement.setString(2, sessionId);
                        statement.setLong(3, expectedVersion);
                        statement.setString(4, leaseOwner);
                        if (statement.executeUpdate() != 1) throw new IllegalStateException("VERSION_CONFLICT");
                    }
                    insertSummary(connection, sessionId, summary);
                    connection.commit();
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (SQLException exception) {
                throw persistence(exception);
            } catch (Exception exception) {
                if (exception instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("PERSISTENCE_ERROR", exception);
            }
        }

        /**
         * 读取不可变模型选项。
         *
         * @return 会话选项
         */
        @Override
        public ModelOptions options() {
            return read().getOptions();
        }

        /**
         * 查询数据库租约是否有效。
         *
         * @return 当前租约有效时为 true
         */
        @Override
        public boolean isBusy() {
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT lease_until>CURRENT_TIMESTAMP FROM agent_session WHERE id=?")) {
                statement.setString(1, sessionId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() && rows.getBoolean(1);
                }
            } catch (SQLException exception) {
                throw persistence(exception);
            }
        }

        /**
         * 校验本运行的持有者标识及有效期仍匹配。
         */
        @Override
        public void requireActiveLease() {
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) FROM agent_session WHERE id=? AND lease_owner=? AND lease_until>CURRENT_TIMESTAMP")) {
                statement.setString(1, sessionId);
                statement.setString(2, leaseOwner);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    if (rows.getLong(1) != 1) throw new IllegalStateException("SESSION_LEASE_LOST");
                }
            } catch (SQLException exception) {
                throw persistence(exception);
            }
        }

        /**
         * 使用独立连接读取状态。
         *
         * @return 已验证会话
         */
        private ImportedSession read() {
            try (Connection connection = database.open()) {
                return read(connection);
            } catch (SQLException exception) {
                throw persistence(exception);
            }
        }

        /**
         * 使用调用者事务连接读取状态。
         *
         * @param connection 当前连接
         * @return 已验证会话
         * @throws SQLException 查询失败时
         */
        private ImportedSession read(Connection connection) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT state_json FROM agent_session WHERE id=?")) {
                statement.setString(1, sessionId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("SESSION_NOT_FOUND");
                    return codec.decode(rows.getString(1));
                }
            }
        }
    }
}
