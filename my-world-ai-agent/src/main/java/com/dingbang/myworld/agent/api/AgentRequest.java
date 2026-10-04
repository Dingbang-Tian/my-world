package com.dingbang.myworld.agent.api;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.content.MediaContentBlock;
import lombok.Data;

import java.util.List;
import java.util.Objects;

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
     * 会话所有者标识，由可信调用方提供。
     */
    private final String ownerId;

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
     * 本次调用的模型选项覆盖值。
     */
    private final ModelOptions modelOptions;

    /**
     * 本次用户输入的附件快照。
     */
    private final List<MediaContentBlock> attachments;

    /**
     * 校验请求的必填字段；兼容入口将 appId 作为 ownerId，服务多用户时应使用显式所有者构造器。
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
        this(appId, appId, agentId, sessionId, requestId, userText, ModelOptions.empty());
    }

    /**
     * 用可信所有者及本次调用选项创建请求。
     *
     * @param ownerId 可信所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param sessionId 既有会话标识，null 表示创建新会话
     * @param requestId 本次请求标识
     * @param userText 用户输入文本
     * @param modelOptions 本次调用选项，非空值覆盖会话选项
     */
    public AgentRequest(String ownerId, String appId, String agentId, String sessionId,
                        String requestId, String userText, ModelOptions modelOptions) {
        this(ownerId, appId, agentId, sessionId, requestId, userText, modelOptions, List.of());
    }

    /**
     * 用可信所有者、调用选项和有界附件创建请求。
     *
     * @param ownerId 可信所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param sessionId 既有会话标识
     * @param requestId 本次请求标识
     * @param userText 用户输入文本
     * @param modelOptions 单次模型选项
     * @param attachments 本次用户消息的附件
     */
    public AgentRequest(String ownerId, String appId, String agentId, String sessionId,
                        String requestId, String userText, ModelOptions modelOptions,
                        List<MediaContentBlock> attachments) {
        if (StringUtils.isBlank(appId) || StringUtils.isBlank(agentId)
                || StringUtils.isBlank(ownerId)
                || StringUtils.isBlank(requestId) || StringUtils.isBlank(userText)
                || (sessionId != null && StringUtils.isBlank(sessionId))) {
            throw new IllegalArgumentException("Agent 请求的应用、Agent、请求标识和用户输入不能为空");
        }
        this.appId = appId;
        this.ownerId = ownerId;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.userText = userText;
        this.modelOptions = Objects.requireNonNull(modelOptions, "模型选项不能为 null");
        this.attachments = List.copyOf(Objects.requireNonNull(attachments, "附件不能为 null"));
    }
}
