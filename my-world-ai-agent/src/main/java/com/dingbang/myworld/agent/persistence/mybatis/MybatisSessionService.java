package com.dingbang.myworld.agent.persistence.mybatis;

import com.dingbang.myworld.agent.memory.MemorySummary;
import com.dingbang.myworld.agent.session.ImportedSession;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 通过 MyBatis-Plus Mapper 保存会话、消息和摘要并维护短租约。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class MybatisSessionService implements SessionRepository {
    /** 会话及附属记录 Mapper。 */
    private final AgentSessionMapper sessions;
    /** 包含多表写入的事务模板。 */
    private final TransactionTemplate transactions;
    /** 会话 JSON 契约。 */
    private final SessionExportCodec codec = new SessionExportCodec();
    /** 读取消息 JSON 树的编解码器。 */
    private final ObjectMapper json = new ObjectMapper();
    /** 只负责续租的守护线程。 */
    private static final ScheduledExecutorService HEARTBEATS = Executors.newSingleThreadScheduledExecutor(task -> {
        /** 不阻止 JVM 结束的续租线程。 */
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
        return sessions.selectById(sessionId) == null ? null : new PersistentSession(sessionId);
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
        /** 经公共会话契约验证的完整 JSON。 */
        String stateJson = codec.encode(sessionId, ownerId, appId, agentId, options,
                new SessionSnapshot(version, history, summary));
        codec.decode(stateJson);
        try {
            transactions.executeWithoutResult(status -> {
                /** 会话主表记录。 */
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
        return new PersistentSession(sessionId);
    }

    /**
     * 将新增消息按稳定序号写入数据库。
     *
     * @param sessionId 会话标识
     * @param existingCount 当前消息数量
     * @param messages 新消息
     * @param stateJson 包含全部消息的 JSON
     */
    private void insertMessages(String sessionId, int existingCount, List<Message> messages, String stateJson) {
        try {
            /** 已验证的顺序消息树。 */
            JsonNode nodes = json.readTree(stateJson).path("messages");
            for (int index = 0; index < messages.size(); index++) {
                /** 当前消息。 */
                Message message = messages.get(index);
                /** 来源运行标识，导入历史可为空。 */
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
    private void insertSummary(String sessionId, MemorySummary summary) {
        sessions.insertSummary(UUID.randomUUID().toString(), sessionId,
                summary.getCoveredMessageCount(), summary.getText());
    }

    /**
     * 获取用于数据库租约比较的 UTC 时间。
     *
     * @return 当前 UTC 时间
     */
    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 提供会话快照和乐观锁操作的轻量句柄。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    private final class PersistentSession implements Session {
        /** 会话标识。 */
        private final String sessionId;
        /** 本句柄的唯一租约标识。 */
        private final String leaseOwner = UUID.randomUUID().toString();
        /** 周期续租任务。 */
        private ScheduledFuture<?> renewal;

        /**
         * 创建会话句柄。
         *
         * @param sessionId 会话标识
         */
        private PersistentSession(String sessionId) {
            this.sessionId = sessionId;
        }

        /**
         * 校验会话归属。
         *
         * @param ownerId 所有者
         * @param appId 应用
         * @param agentId Agent
         */
        @Override
        public void requireOwner(String ownerId, String appId, String agentId) {
            /** 数据库中的会话。 */
            AgentSessionRow row = sessions.selectById(sessionId);
            if (row == null || !row.getOwnerKey().equals(ownerId) || !row.getAppId().equals(appId)
                    || !row.getAgentId().equals(agentId)) {
                throw new IllegalArgumentException("会话归属不匹配");
            }
        }

        /**
         * 以数据库条件更新取得六十秒租约并启动续租。
         *
         * @return 是否取得租约
         */
        @Override
        public synchronized boolean tryStart() {
            /** 本次比较使用的 UTC 时间。 */
            LocalDateTime current = now();
            if (sessions.acquireLease(sessionId, leaseOwner, current, current.plusSeconds(60)) == 0) return false;
            renewal = HEARTBEATS.scheduleAtFixedRate(this::renewLease, 20, 20, TimeUnit.SECONDS);
            return true;
        }

        /**
         * 只为当前句柄持有的租约续期。
         */
        private void renewLease() {
            try {
                sessions.renewLease(sessionId, leaseOwner, now().plusSeconds(60));
            } catch (RuntimeException ignored) {
                // 下一次续租仍会尝试，写入前会再次验证有效租约。
            }
        }

        /**
         * 停止续租并释放当前句柄的租约。
         */
        @Override
        public synchronized void release() {
            if (renewal != null) renewal.cancel(false);
            sessions.releaseLease(sessionId, leaseOwner);
        }

        /**
         * 读取已经提交的历史和摘要。
         *
         * @return 会话快照
         */
        @Override
        public SessionSnapshot snapshot() {
            /** 已提交会话。 */
            ImportedSession stored = read();
            return new SessionSnapshot(stored.getVersion(), stored.getMessages(), stored.getSummary());
        }

        /**
         * 在短事务中原子提交版本、快照和消息行。
         *
         * @param expectedVersion 预期历史版本
         * @param messages 本轮完整消息
         */
        @Override
        public void appendExchange(long expectedVersion, List<Message> messages) {
            Objects.requireNonNull(messages, "消息不能为 null");
            transactions.executeWithoutResult(status -> {
                /** 当前已提交状态。 */
                ImportedSession stored = read();
                if (stored.getVersion() != expectedVersion) throw new IllegalStateException("VERSION_CONFLICT");
                /** 完整新历史。 */
                List<Message> history = new ArrayList<>(stored.getMessages());
                history.addAll(messages);
                /** 新状态 JSON。 */
                String stateJson = codec.encode(sessionId, stored.getOwnerId(), stored.getAppId(),
                        stored.getAgentId(), stored.getOptions(),
                        new SessionSnapshot(expectedVersion + 1, history, stored.getSummary()));
                codec.decode(stateJson);
                if (sessions.appendState(sessionId, expectedVersion, leaseOwner, now(), stateJson) != 1) {
                    throw new IllegalStateException("VERSION_CONFLICT");
                }
                insertMessages(sessionId, stored.getMessages().size(), messages, stateJson);
            });
        }

        /**
         * 在版本和租约匹配时前移摘要覆盖位置。
         *
         * @param expectedVersion 预期历史版本
         * @param expectedCoveredMessageCount 旧摘要覆盖数
         * @param summary 新摘要
         */
        @Override
        public void updateSummary(long expectedVersion, int expectedCoveredMessageCount, MemorySummary summary) {
            transactions.executeWithoutResult(status -> {
                /** 当前已提交状态。 */
                ImportedSession stored = read();
                if (stored.getVersion() != expectedVersion
                        || (stored.getSummary() == null ? 0 : stored.getSummary().getCoveredMessageCount())
                        != expectedCoveredMessageCount
                        || summary.getCoveredMessageCount() <= expectedCoveredMessageCount) {
                    throw new IllegalStateException("VERSION_CONFLICT");
                }
                /** 新状态 JSON。 */
                String stateJson = codec.encode(sessionId, stored.getOwnerId(), stored.getAppId(),
                        stored.getAgentId(), stored.getOptions(),
                        new SessionSnapshot(expectedVersion, stored.getMessages(), summary));
                codec.decode(stateJson);
                if (sessions.updateSummaryState(sessionId, expectedVersion, leaseOwner, now(), stateJson) != 1) {
                    throw new IllegalStateException("VERSION_CONFLICT");
                }
                insertSummary(sessionId, summary);
            });
        }

        /**
         * 读取已保存的会话选项。
         *
         * @return 会话选项
         */
        @Override
        public ModelOptions options() {
            return read().getOptions();
        }

        /**
         * 查询当前会话是否存在有效租约。
         *
         * @return 存在有效租约时为真
         */
        @Override
        public boolean isBusy() {
            return sessions.busyCount(sessionId, now()) > 0;
        }

        /**
         * 在工具执行前校验本句柄仍持有有效租约。
         */
        @Override
        public void requireActiveLease() {
            if (sessions.activeLeaseCount(sessionId, leaseOwner, now()) != 1) {
                throw new IllegalStateException("SESSION_LEASE_LOST");
            }
        }

        /**
         * 读取并验证已提交的会话 JSON。
         *
         * @return 已提交会话
         */
        private ImportedSession read() {
            /** 会话主表记录。 */
            AgentSessionRow row = sessions.selectById(sessionId);
            if (row == null) throw new IllegalStateException("SESSION_NOT_FOUND");
            return codec.decode(row.getStateJson());
        }
    }
}
