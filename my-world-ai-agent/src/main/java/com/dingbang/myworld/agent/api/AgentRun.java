package com.dingbang.myworld.agent.api;

import java.util.concurrent.CompletionStage;

/**
 * 一次已准备、可显式启动并被多个观察者监听的 Agent 运行。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public interface AgentRun {

    /**
     * 获取预先分配的运行标识。
     *
     * @return 运行标识
     */
    String getRunId();

    /**
     * 获取所属会话标识。
     *
     * @return 会话标识
     */
    String getSessionId();

    /**
     * 注册当前运行的事件监听器，并立即回放最近保留的事件。
     *
     * @param listener 接收事件和结束通知的监听器
     */
    void subscribe(AgentEventListener listener);

    /**
     * 取得同一次运行的最终结果 Future；运行失败也返回 FAILED 结果。
     *
     * @return 最终结果阶段
     */
    CompletionStage<AgentResult> getResult();

    /**
     * 异步启动一次运行；第二次调用立即拒绝。
     *
     * @throws IllegalStateException 当前运行已经启动时
     */
    void execute();
}
