package com.dingbang.myworld.agent.session;

import com.dingbang.myworld.aiframework.api.ModelOptions;

/**
 * 对可信应用公开会话创建、读取、导出与恢复操作。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public interface AgentSessionService {
    /**
     * 创建带会话级模型选项的空会话。
     *
     * @param ownerId 可信所有者标识
     * @param appId 应用标识
     * @param agentId 已注册 Agent 标识
     * @param options 会话选项
     * @return 新会话标识
     */
    String createSession(String ownerId, String appId, String agentId, ModelOptions options);

    /**
     * 读取指定归属会话的完整历史。
     *
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param sessionId 会话标识
     * @return 历史与版本快照
     */
    SessionSnapshot getSession(String ownerId, String appId, String agentId, String sessionId);

    /**
     * 导出未运行会话的数据 JSON，不包含客户端或凭据。
     *
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param sessionId 会话标识
     * @return 版本化 JSON
     */
    String exportSession(String ownerId, String appId, String agentId, String sessionId);

    /**
     * 将经过归属和格式校验的会话绑定到当前可信 Agent 定义。
     *
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId 已注册 Agent 标识
     * @param serialized 版本化会话 JSON
     * @return 导入后的会话标识
     */
    String importSession(String ownerId, String appId, String agentId, String serialized);
}
