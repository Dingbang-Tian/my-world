package com.dingbang.myworld.aiframework.model;

import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.common.utils.collection.CollectionUtils;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 不依赖模型供应商的不可变对话消息。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Data
public final class Message {

    /**
     * 消息标识。
     */
    private final String messageId;

    /**
     * 消息角色。
     */
    private final Role role;

    /**
     * 内容块快照。
     */
    private final List<ContentBlock> contentBlocks;

    /**
     * 助手提出的工具调用快照。
     */
    private final List<ToolCall> toolCalls;

    /**
     * 工具消息携带的执行结果快照。
     */
    private final List<ToolResult> toolResults;

    /**
     * 供应商协议元数据快照。
     */
    private final Map<String, String> providerMetadata;

    /**
     * 校验必要字段并复制集合，避免调用者修改已创建的消息。
     *
     * @param messageId 消息标识
     * @param role 消息角色
     * @param contentBlocks 内容块
     * @param toolCalls 工具调用
     * @param toolResults 工具结果
     * @param providerMetadata 供应商协议元数据
     * @throws IllegalArgumentException 当消息标识为空、角色与工具数据不匹配或工具调用标识重复时
     * @throws NullPointerException 当必要字段或集合为 null 时
     */
    public Message(String messageId, Role role, List<ContentBlock> contentBlocks,
                   List<ToolCall> toolCalls, List<ToolResult> toolResults,
                   Map<String, String> providerMetadata) {
        // 数据校验
        if (StringUtils.isBlank(messageId)) {
            throw new IllegalArgumentException("消息标识不能为空");
        }
        Objects.requireNonNull(contentBlocks, "内容块列表不能为 null").forEach(Objects::requireNonNull);
        /** 已校验的工具调用列表。 */
        List<ToolCall> validatedToolCalls = Objects.requireNonNull(toolCalls, "工具调用列表不能为 null");
        validatedToolCalls.forEach(Objects::requireNonNull);
        /** 当前消息中已经出现的工具调用标识。 */
        Set<String> callIds = new HashSet<>();
        for (ToolCall toolCall : validatedToolCalls) {
            if (!callIds.add(toolCall.getCallId())) {
                throw new IllegalArgumentException("同一助手消息中的工具调用标识不能重复: "
                        + toolCall.getCallId());
            }
        }
        Objects.requireNonNull(toolResults, "工具结果列表不能为 null").forEach(Objects::requireNonNull);
        Objects.requireNonNull(providerMetadata, "协议元数据不能为 null");

        // 业务校验
        if (CollectionUtils.isNotEmpty(toolCalls) && role != Role.ASSISTANT) {
            throw new IllegalArgumentException("只有助手消息可以发起工具调用");
        }
        if (CollectionUtils.isNotEmpty(toolResults) && role != Role.TOOL) {
            throw new IllegalArgumentException("只有工具消息可以携带工具结果");
        }
        this.messageId = messageId;
        this.role = Objects.requireNonNull(role, "消息角色不能为 null");
        this.contentBlocks = Collections.unmodifiableList(new ArrayList<>(contentBlocks));
        this.toolCalls = Collections.unmodifiableList(new ArrayList<>(validatedToolCalls));
        this.toolResults = Collections.unmodifiableList(new ArrayList<>(toolResults));
        this.providerMetadata = Collections.unmodifiableMap(new LinkedHashMap<>(providerMetadata));
    }

}
