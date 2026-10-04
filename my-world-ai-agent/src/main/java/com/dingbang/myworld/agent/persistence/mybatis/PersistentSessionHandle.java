package com.dingbang.myworld.agent.persistence.mybatis;

import com.dingbang.myworld.agent.memory.MemorySummary;
import com.dingbang.myworld.agent.session.ImportedSession;
import com.dingbang.myworld.agent.session.Session;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 持久化会话操作句柄。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PersistentSessionHandle implements Session {
    /**
     * 原运行对象，供回调访问当前状态。
     */
    private final MybatisSessionService outer;

    /**
     * 会话标识。
     */
    private final String sessionId;
    /**
     * 本句柄的唯一租约标识。
     */
    private final String leaseOwner = UUID.randomUUID().toString();
    /**
     * 周期续租任务。
     */
    private ScheduledFuture<?> renewal;

    /**
     * 校验会话归属。
     *
     * @param ownerId 所有者
     * @param appId 应用
     * @param agentId Agent
     */
    @Override
    public void requireOwner(String ownerId, String appId, String agentId) {
        // 数据库中的会话。
        AgentSessionRow row = outer.sessions.selectById(sessionId);
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
        // 本次比较使用的 UTC 时间。
        LocalDateTime current = MybatisSessionService.now();
        if (outer.sessions.acquireLease(sessionId, leaseOwner, current, current.plusSeconds(60)) == 0) return false;
        renewal = MybatisSessionService.HEARTBEATS.scheduleAtFixedRate(this::renewLease, 20, 20, TimeUnit.SECONDS);
        return true;
    }

    /**
     * 只为当前句柄持有的租约续期。
     */
    private void renewLease() {
        try {
            outer.sessions.renewLease(sessionId, leaseOwner, MybatisSessionService.now().plusSeconds(60));
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
        outer.sessions.releaseLease(sessionId, leaseOwner);
    }

    /**
     * 读取已经提交的历史和摘要。
     *
     * @return 会话快照
     */
    @Override
    public SessionSnapshot snapshot() {
        // 已提交会话。
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
        outer.transactions.executeWithoutResult(status -> {
            // 当前已提交状态。
            ImportedSession stored = read();
            if (stored.getVersion() != expectedVersion) throw new IllegalStateException("VERSION_CONFLICT");
            // 完整新历史。
            List<Message> history = new ArrayList<>(stored.getMessages());
            history.addAll(messages);
            // 新状态 JSON。
            String stateJson = outer.codec.encode(sessionId, stored.getOwnerId(), stored.getAppId(),
                    stored.getAgentId(), stored.getOptions(),
                    new SessionSnapshot(expectedVersion + 1, history, stored.getSummary()));
            outer.codec.decode(stateJson);
            if (outer.sessions.appendState(sessionId, expectedVersion, leaseOwner, MybatisSessionService.now(), stateJson) != 1) {
                throw new IllegalStateException("VERSION_CONFLICT");
            }
            outer.insertMessages(sessionId, stored.getMessages().size(), messages, stateJson);
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
        outer.transactions.executeWithoutResult(status -> {
            // 当前已提交状态。
            ImportedSession stored = read();
            if (stored.getVersion() != expectedVersion
                    || (stored.getSummary() == null ? 0 : stored.getSummary().getCoveredMessageCount())
                    != expectedCoveredMessageCount
                    || summary.getCoveredMessageCount() <= expectedCoveredMessageCount) {
                throw new IllegalStateException("VERSION_CONFLICT");
            }
            // 新状态 JSON。
            String stateJson = outer.codec.encode(sessionId, stored.getOwnerId(), stored.getAppId(),
                    stored.getAgentId(), stored.getOptions(),
                    new SessionSnapshot(expectedVersion, stored.getMessages(), summary));
            outer.codec.decode(stateJson);
            if (outer.sessions.updateSummaryState(sessionId, expectedVersion, leaseOwner, MybatisSessionService.now(), stateJson) != 1) {
                throw new IllegalStateException("VERSION_CONFLICT");
            }
            outer.insertSummary(sessionId, summary);
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
        return outer.sessions.busyCount(sessionId, MybatisSessionService.now()) > 0;
    }

    /**
     * 在工具执行前校验本句柄仍持有有效租约。
     */
    @Override
    public void requireActiveLease() {
        if (outer.sessions.activeLeaseCount(sessionId, leaseOwner, MybatisSessionService.now()) != 1) {
            throw new IllegalStateException("SESSION_LEASE_LOST");
        }
    }

    /**
     * 读取并验证已提交的会话 JSON。
     *
     * @return 已提交会话
     */
    private ImportedSession read() {
        // 会话主表记录。
        AgentSessionRow row = outer.sessions.selectById(sessionId);
        if (row == null) throw new IllegalStateException("SESSION_NOT_FOUND");
        return outer.codec.decode(row.getStateJson());
    }
}
