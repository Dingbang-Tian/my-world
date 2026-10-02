package com.dingbang.myworld.agent.tool;

import lombok.Data;

import java.util.Objects;

/**
 * Java 工具成功执行后的文本输出。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class ToolExecutionResult {
    /**
     * 返回给模型的内容。
     */
    private final String content;
    /**
     * 内容是否被工具截断。
     */
    private final boolean truncated;

    /**
     * 创建工具输出。
     *
     * @param content 返回给模型的内容
     * @param truncated 内容是否被截断
     */
    public ToolExecutionResult(String content, boolean truncated) {
        this.content = Objects.requireNonNull(content, "工具内容不能为 null");
        this.truncated = truncated;
    }

    /**
     * 创建未截断的文本输出。
     *
     * @param content 返回给模型的内容
     * @return 工具输出
     */
    public static ToolExecutionResult text(String content) {
        return new ToolExecutionResult(content, false);
    }
}
