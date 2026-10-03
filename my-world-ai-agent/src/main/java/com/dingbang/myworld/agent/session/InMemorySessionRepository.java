package com.dingbang.myworld.agent.session;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 在同一进程内原子保存会话身份、历史和版本。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class InMemorySessionRepository implements SessionRepository {
    /** 会话标识对应的会话。 */
    private final ConcurrentMap<String, MemorySession> sessions = new ConcurrentHashMap<>();

    /**
     * 原子创建空会话。
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
        return add(new MemorySession(sessionId, ownerId, appId, agentId, 0, options, List.of()));
    }

    /**
     * 查找同一进程中的会话。
     *
     * @param sessionId 会话标识
     * @return 会话或 null
     */
    @Override
    public Session find(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 原子登记经过验证的导入会话。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param version 历史版本
     * @param options 会话选项
     * @param history 完整历史
     * @return 导入会话
     */
    @Override
    public Session importSession(String sessionId, String ownerId, String appId, String agentId,
                                 long version, ModelOptions options, List<Message> history) {
        return add(new MemorySession(sessionId, ownerId, appId, agentId, version, options, history));
    }

    /**
     * 拒绝覆盖任何已有会话。
     *
     * @param session 待登记会话
     * @return 已登记会话
     */
    private Session add(MemorySession session) {
        if (sessions.putIfAbsent(session.getSessionId(), session) != null) {
            throw new IllegalStateException("SESSION_ALREADY_EXISTS");
        }
        return session;
    }
}
