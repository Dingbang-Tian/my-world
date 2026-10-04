package com.dingbang.myworld.agent.api;

/**
 * Agent 运行中的状态及其终态种类。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public enum AgentResultStatus {
    /**
     * 运行正在执行，尚无最终结果。
     */
    RUNNING,
    /**
     * 正常完成。
     */
    COMPLETED,
    /**
     * 执行失败。
     */
    FAILED,
    /**
     * 已取消。
     */
    CANCELLED,
    /**
     * 运行超时。
     */
    TIMED_OUT,
    /**
     * 达到运行限制。
     */
    LIMIT_EXCEEDED,
    /**
     * 进程中断。
     */
    INTERRUPTED,
    /**
     * 存在无法确定是否已发生的副作用，等待显式核查。
     */
    NEEDS_REVIEW
}
