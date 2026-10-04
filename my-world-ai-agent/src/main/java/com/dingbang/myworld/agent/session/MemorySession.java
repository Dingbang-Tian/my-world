package com.dingbang.myworld.agent.session;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.agent.memory.MemorySummary;
import com.dingbang.myworld.common.utils.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 保存单个内存会话的身份、历史、版本和运行占用状态。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
final class MemorySession implements Session {

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
     * 会话级模型选项。
     */
    private final ModelOptions options;

    /**
     * 已完成的原始消息。
     */
    private final List<Message> history;

    /**
     * 成功提交的交换次数。
     */
    private long version;
    /**
     * 已提交的有损摘要。
     */
    private MemorySummary summary;

    /**
     * 是否有运行占用此会话。
     */
    private boolean busy;

    /**
     * 初始化内存会话状态。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param version 历史版本
     * @param options 会话选项
     * @param history 初始完整历史
     */
    MemorySession(String sessionId, String ownerId, String appId, String agentId,
                  long version, ModelOptions options, List<Message> history) {
        this(sessionId, ownerId, appId, agentId, version, options, history, null);
    }

    /**
     * 初始化包含摘要的内存会话。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param version 历史版本
     * @param options 会话模型选项
     * @param history 完整原始历史
     * @param summary 已提交摘要，可为 null
     */
    MemorySession(String sessionId, String ownerId, String appId, String agentId,
                  long version, ModelOptions options, List<Message> history, MemorySummary summary) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(ownerId)
                || StringUtils.isBlank(appId) || StringUtils.isBlank(agentId)) {
            throw new IllegalArgumentException("会话身份不能为空");
        }
        this.sessionId = sessionId;
        this.ownerId = ownerId;
        this.appId = appId;
        this.agentId = agentId;
        this.options = Objects.requireNonNull(options, "会话模型选项不能为 null");
        if (version < 0) {
            throw new IllegalArgumentException("会话版本不能为负数");
        }
        this.version = version;
        this.history = new ArrayList<>(Objects.requireNonNull(history, "会话历史不能为 null"));
        this.history.forEach(Objects::requireNonNull);
        if ((version == 0 && !this.history.isEmpty()) || (version > 0 && this.history.isEmpty())) {
            throw new IllegalArgumentException("会话版本与历史不一致");
        }
        if (!this.history.isEmpty()) {
            SessionHistoryValidator.validateExchange(this.history);
        }
        validateSummary(this.history, summary);
        this.summary = summary;
    }

    /**
     * 获取仓库存储使用的会话标识。
     *
     * @return 会话标识
     */
    String getSessionId() {
        return sessionId;
    }

    /**
     * 校验 owner、应用及 Agent 三重归属。
     *
     * @param expectedOwnerId 调用者所有者
     * @param expectedAppId 调用者应用
     * @param expectedAgentId 调用者 Agent
     */
    @Override
    public void requireOwner(String expectedOwnerId, String expectedAppId, String expectedAgentId) {
        if (!ownerId.equals(expectedOwnerId) || !appId.equals(expectedAppId)
                || !agentId.equals(expectedAgentId)) {
            throw new IllegalArgumentException("会话归属不匹配");
        }
    }

    /**
     * 独占开始会话运行。
     *
     * @return 未被占用时为 true
     */
    @Override
    public synchronized boolean tryStart() {
        if (busy) {
            return false;
        }
        busy = true;
        return true;
    }

    /**
     * 释放运行权。
     */
    @Override
    public synchronized void release() {
        busy = false;
    }

    /**
     * 复制当前历史和版本。
     *
     * @return 同一时点的快照
     */
    @Override
    public synchronized SessionSnapshot snapshot() {
        return new SessionSnapshot(version, history, summary);
    }

    /**
     * 按预期版本提交完整交换。
     *
     * @param expectedVersion 读取历史时的版本
     * @param messages 完整交换消息
     */
    @Override
    public synchronized void appendExchange(long expectedVersion, List<Message> messages) {
        if (!busy || version != expectedVersion) {
            throw new IllegalStateException("VERSION_CONFLICT");
        }
        SessionHistoryValidator.validateExchange(messages);
        history.addAll(messages);
        version++;
    }

    /**
     * 原子提交已验证的摘要覆盖位置。
     *
     * @param expectedVersion 历史版本
     * @param expectedCoveredMessageCount 原覆盖位置
     * @param nextSummary 新摘要
     */
    @Override
    public synchronized void updateSummary(long expectedVersion, int expectedCoveredMessageCount,
                                           MemorySummary nextSummary) {
        if (!busy || version != expectedVersion
                || (summary == null ? 0 : summary.getCoveredMessageCount()) != expectedCoveredMessageCount) {
            throw new IllegalStateException("VERSION_CONFLICT");
        }
        validateSummary(history, nextSummary);
        if (nextSummary.getCoveredMessageCount() <= expectedCoveredMessageCount) {
            throw new IllegalArgumentException("摘要覆盖位置必须前进");
        }
        summary = nextSummary;
    }

    /**
     * 校验覆盖位置恰好落在完整交换末尾。
     *
     * @param messages 完整原始历史
     * @param candidate 待校验摘要
     */
    private static void validateSummary(List<Message> messages, MemorySummary candidate) {
        if (candidate == null) return;
        if (candidate.getCoveredMessageCount() > messages.size()) {
            throw new IllegalArgumentException("摘要覆盖位置超出历史");
        }
        SessionHistoryValidator.validateExchange(messages.subList(0, candidate.getCoveredMessageCount()));
    }

    /**
     * 获取会话级选项。
     *
     * @return 不可变模型选项
     */
    @Override
    public ModelOptions options() {
        return options;
    }

    /**
     * 查询是否正在执行。
     *
     * @return 正在执行时为 true
     */
    @Override
    public synchronized boolean isBusy() {
        return busy;
    }
}
