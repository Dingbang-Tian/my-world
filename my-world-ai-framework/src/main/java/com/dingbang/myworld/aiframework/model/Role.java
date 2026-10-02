package com.dingbang.myworld.aiframework.model;

/**
 * 协议中立的对话消息角色。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
public enum Role {
    /**
     * 系统指令。
     */
    SYSTEM,
    /**
     * 用户输入。
     */
    USER,
    /**
     * 模型助手输出。
     */
    ASSISTANT,
    /**
     * 工具执行结果。
     */
    TOOL
}
