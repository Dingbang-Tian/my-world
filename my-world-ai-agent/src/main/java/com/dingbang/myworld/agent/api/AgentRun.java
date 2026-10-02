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
     * 注册当前运行的事件监听器，异步回放完整可用历史；历史缺口会抛出可识别错误。
     *
     * @param listener 接收事件和结束通知的监听器
     */
    void subscribe(AgentEventListener listener);

    /**
     * 从指定序号之后异步回放；序号零表示从头订阅。
     *
     * @param listener 事件消费者
     * @param afterSequence 已成功接收的最后序号
     * @throws AgentEventException 请求的历史已被淘汰或序号无效时
     */
    void subscribe(AgentEventListener listener, long afterSequence);

    /**
     * 幂等结束运行并通知模型和工具停止；执行前也可取消。
     */
    void cancel();

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
