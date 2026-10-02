package com.dingbang.myworld.aiframework.model;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

import java.util.Objects;

/**
 * 模型提出的工具调用数据，不包含 Java 工具执行对象。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Data
public final class ToolCall {

    /**
     * 本次工具调用标识。
     */
    private final String callId;

    /**
     * 工具名称。
     */
    private final String name;

    /**
     * 完整参数 JSON 字符串。
     */
    private final String argumentsJson;

    /**
     * 校验工具调用标识、名称和参数文本。
     *
     * @param callId 本次工具调用标识
     * @param name 工具名称
     * @param argumentsJson 完整参数 JSON 字符串
     * @throws IllegalArgumentException 当标识或名称为空时
     * @throws NullPointerException 当参数文本为 null 时
     */
    public ToolCall(String callId, String name, String argumentsJson) {
        if (StringUtils.isBlank(callId)) {
            throw new IllegalArgumentException("工具调用标识不能为空");
        }
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        this.callId = callId;
        this.name = name;
        this.argumentsJson = Objects.requireNonNull(argumentsJson, "工具参数 JSON 不能为 null");
    }

}
