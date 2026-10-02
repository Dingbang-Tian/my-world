package com.dingbang.myworld.aiframework.model;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

import java.util.Objects;

/**
 * 与工具调用标识关联的结构化执行结果。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Data
public final class ToolResult {

    /**
     * 对应的工具调用标识。
     */
    private final String callId;

    /**
     * 工具执行状态。
     */
    private final ToolResultStatus status;

    /**
     * 返回给模型的内容。
     */
    private final String content;

    /**
     * 失败代码，成功时为 null。
     */
    private final String errorCode;

    /**
     * 内容是否因输出限制被截断。
     */
    private final boolean truncated;

    /**
     * 校验执行结果的必要字段。
     *
     * @param callId 对应的工具调用标识
     * @param status 工具执行状态
     * @param content 返回给模型的内容
     * @param errorCode 失败代码，成功时为 null
     * @param truncated 内容是否被截断
     * @throws IllegalArgumentException 当调用标识为空时
     * @throws NullPointerException 当状态或内容为 null 时
     */
    public ToolResult(String callId, ToolResultStatus status, String content,
                      String errorCode, boolean truncated) {
        if (StringUtils.isBlank(callId)) {
            throw new IllegalArgumentException("工具调用标识不能为空");
        }
        this.callId = callId;
        this.status = Objects.requireNonNull(status, "工具状态不能为 null");
        this.content = Objects.requireNonNull(content, "工具内容不能为 null");
        this.errorCode = errorCode;
        this.truncated = truncated;
    }
}
