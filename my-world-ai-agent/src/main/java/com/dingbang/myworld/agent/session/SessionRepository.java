package com.dingbang.myworld.agent.session;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;

import java.util.List;

/**
 * 按会话标识保存归属、版本和完整消息的可替换存储契约。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public interface SessionRepository {

    /**
     * 创建空会话；已有标识必须拒绝。
     *
     * @param sessionId 新会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param options 会话级模型选项
     * @return 新会话
     */
    Session create(String sessionId, String ownerId, String appId, String agentId, ModelOptions options);

    /**
     * 按标识读取会话。
     *
     * @param sessionId 会话标识
     * @return 会话，不存在时为 null
     */
    Session find(String sessionId);

    /**
     * 保存导入的完整会话；已有标识必须拒绝。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param version 历史版本
     * @param options 会话级模型选项
     * @param history 已验证的完整消息
     * @return 导入的会话
     */
    Session importSession(String sessionId, String ownerId, String appId, String agentId,
                          long version, ModelOptions options, List<Message> history);
}
