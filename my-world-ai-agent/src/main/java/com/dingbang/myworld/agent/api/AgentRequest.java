package com.dingbang.myworld.agent.api;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

/**
 * 一次 Agent 用户输入及其应用和会话归属。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class AgentRequest {

    /**
     * 所属应用标识。
     */
    private final String appId;

    /**
     * Agent 标识。
     */
    private final String agentId;

    /**
     * 既有会话标识；null 表示创建新会话。
     */
    private final String sessionId;

    /**
     * 本次请求标识，后续阶段用于幂等处理。
     */
    private final String requestId;

    /**
     * 用户输入文本，只能作为 USER 消息发送。
     */
    private final String userText;

    /**
     * 校验请求的必填字段。
     *
     * @param appId 所属应用标识
     * @param agentId Agent 标识
     * @param sessionId 既有会话标识，null 表示新会话
     * @param requestId 请求标识
     * @param userText 用户输入文本
     * @throws IllegalArgumentException 标识或用户输入为空时
     */
    public AgentRequest(String appId, String agentId, String sessionId,
                        String requestId, String userText) {
        if (StringUtils.isBlank(appId) || StringUtils.isBlank(agentId)
                || StringUtils.isBlank(requestId) || StringUtils.isBlank(userText)
                || (sessionId != null && StringUtils.isBlank(sessionId))) {
            throw new IllegalArgumentException("Agent 请求的应用、Agent、请求标识和用户输入不能为空");
        }
        this.appId = appId;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.userText = userText;
    }
}
