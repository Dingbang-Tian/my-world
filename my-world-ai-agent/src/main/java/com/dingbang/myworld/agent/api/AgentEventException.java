package com.dingbang.myworld.agent.api;

import lombok.Getter;

/**
 * 表示事件历史缺口、慢消费者或订阅调度失败。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Getter
public final class AgentEventException extends RuntimeException {
    /**
     * 可供客户端切换结果查询的稳定错误码。
     */
    private final String code;
    /**
     * 当前可用历史的第一条序号。
     */
    private final long firstAvailableSequence;

    /**
     * 创建订阅错误。
     *
     * @param code 错误码
     * @param message 说明
     * @param firstAvailableSequence 当前可用历史起点
     */
    public AgentEventException(String code, String message, long firstAvailableSequence) {
        super(message);
        this.code = code;
        this.firstAvailableSequence = firstAvailableSequence;
    }

}
