package com.dingbang.myworld.agent.tool;

/**
 * 工具调用的可观察阶段。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public enum ToolExecutionPhase {
    /**
     * 准备校验参数。
     */
    PREPARING,
    /**
     * 正在调用 Java 工具。
     */
    CALLING,
    /**
     * Java 工具已成功返回。
     */
    COMPLETED,
    /**
     * 校验或执行失败。
     */
    FAILED
}
