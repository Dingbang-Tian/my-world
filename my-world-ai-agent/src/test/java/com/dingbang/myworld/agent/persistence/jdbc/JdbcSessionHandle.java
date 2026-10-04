package com.dingbang.myworld.agent.persistence.jdbc;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.agent.memory.MemorySummary;
import com.dingbang.myworld.agent.session.ImportedSession;
import com.dingbang.myworld.agent.session.Session;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * JDBC 测试会话操作句柄。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class JdbcSessionHandle implements Session {
    /**
     * 原运行对象，供回调访问当前状态。
     */
    private final JdbcSessionRepository outer;

    /**
     * 会话标识。
     */
    private final String sessionId;
    /**
     * 当前句柄的唯一租约持有者。
     */
    private final String leaseOwner = UUID.randomUUID().toString();
    /**
     * 租约续期任务。
     */
    private ScheduledFuture<?> renewal;

    /**
     * 创建轻量级会话句柄。
     *
     * @param sessionId 会话标识
     */
    JdbcSessionHandle(JdbcSessionRepository outer, String sessionId) {
        this.outer = outer;
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
        try (Connection connection = outer.database.open();
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
            throw outer.persistence(exception);
        }
    }

    /**
     * 以数据库条件更新取得一分钟租约并安排续期。
     *
     * @return 成功取得时为 true
     */
    @Override
    public synchronized boolean tryStart() {
        try (Connection connection = outer.database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE agent_session SET lease_owner=?,lease_until=DATEADD('SECOND',60,CURRENT_TIMESTAMP) WHERE id=? AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)")) {
            statement.setString(1, leaseOwner);
            statement.setString(2, sessionId);
            if (statement.executeUpdate() == 0) return false;
            renewal = JdbcSessionRepository.HEARTBEATS.scheduleAtFixedRate(this::renewLease, 20, 20, TimeUnit.SECONDS);
            return true;
        } catch (SQLException exception) {
            throw outer.persistence(exception);
        }
    }

    /**
     * 仅为仍由本句柄持有的租约延长有效期。
     */
    private void renewLease() {
        try (Connection connection = outer.database.open();
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
        try (Connection connection = outer.database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE agent_session SET lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=?")) {
            statement.setString(1, sessionId);
            statement.setString(2, leaseOwner);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw outer.persistence(exception);
        }
    }

    /**
     * 从单行已提交状态读取历史与摘要。
     *
     * @return 一致的会话快照
     */
    @Override
    public SessionSnapshot snapshot() {
        // 当前已提交的会话。
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
        try (Connection connection = outer.database.open()) {
            connection.setAutoCommit(false);
            try {
                // 当前状态。
                ImportedSession stored = read(connection);
                if (stored.getVersion() != expectedVersion) throw new IllegalStateException("VERSION_CONFLICT");
                // 添加完整交换后的历史。
                List<Message> history = new ArrayList<>(stored.getMessages());
                history.addAll(messages);
                // 编解码同时校验消息配对和摘要边界。
                String json = outer.codec.encode(sessionId, stored.getOwnerId(), stored.getAppId(),
                        stored.getAgentId(), stored.getOptions(),
                        new SessionSnapshot(expectedVersion + 1, history, stored.getSummary()));
                outer.codec.decode(json);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE agent_session SET version=?,state_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=? AND lease_owner=? AND lease_until>CURRENT_TIMESTAMP")) {
                    statement.setLong(1, expectedVersion + 1);
                    statement.setString(2, json);
                    statement.setString(3, sessionId);
                    statement.setLong(4, expectedVersion);
                    statement.setString(5, leaseOwner);
                    if (statement.executeUpdate() != 1) throw new IllegalStateException("VERSION_CONFLICT");
                }
                outer.insertMessages(connection, sessionId, stored.getMessages().size(), messages, json);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw outer.persistence(exception);
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
        try (Connection connection = outer.database.open()) {
            connection.setAutoCommit(false);
            try {
                // 已提交会话。
                ImportedSession stored = read(connection);
                if (stored.getVersion() != expectedVersion
                        || (stored.getSummary() == null ? 0 : stored.getSummary().getCoveredMessageCount())
                        != expectedCoveredMessageCount
                        || summary.getCoveredMessageCount() <= expectedCoveredMessageCount) {
                    throw new IllegalStateException("VERSION_CONFLICT");
                }
                // 包含新摘要的序列化状态。
                String json = outer.codec.encode(sessionId, stored.getOwnerId(), stored.getAppId(),
                        stored.getAgentId(), stored.getOptions(),
                        new SessionSnapshot(expectedVersion, stored.getMessages(), summary));
                outer.codec.decode(json);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE agent_session SET state_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=? AND lease_owner=? AND lease_until>CURRENT_TIMESTAMP")) {
                    statement.setString(1, json);
                    statement.setString(2, sessionId);
                    statement.setLong(3, expectedVersion);
                    statement.setString(4, leaseOwner);
                    if (statement.executeUpdate() != 1) throw new IllegalStateException("VERSION_CONFLICT");
                }
                outer.insertSummary(connection, sessionId, summary);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw outer.persistence(exception);
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
        try (Connection connection = outer.database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT lease_until>CURRENT_TIMESTAMP FROM agent_session WHERE id=?")) {
            statement.setString(1, sessionId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        } catch (SQLException exception) {
            throw outer.persistence(exception);
        }
    }

    /**
     * 校验本运行的持有者标识及有效期仍匹配。
     */
    @Override
    public void requireActiveLease() {
        try (Connection connection = outer.database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM agent_session WHERE id=? AND lease_owner=? AND lease_until>CURRENT_TIMESTAMP")) {
            statement.setString(1, sessionId);
            statement.setString(2, leaseOwner);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                if (rows.getLong(1) != 1) throw new IllegalStateException("SESSION_LEASE_LOST");
            }
        } catch (SQLException exception) {
            throw outer.persistence(exception);
        }
    }

    /**
     * 使用独立连接读取状态。
     *
     * @return 已验证会话
     */
    private ImportedSession read() {
        try (Connection connection = outer.database.open()) {
            return read(connection);
        } catch (SQLException exception) {
            throw outer.persistence(exception);
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
                return outer.codec.decode(rows.getString(1));
            }
        }
    }
}
