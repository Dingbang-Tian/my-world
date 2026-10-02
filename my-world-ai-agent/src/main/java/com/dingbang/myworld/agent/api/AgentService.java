package com.dingbang.myworld.agent.api;

/**
 * 对应用公开的 Agent 准备、执行和同步便利入口。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public interface AgentService {

    /**
     * 验证请求并创建运行句柄；不调用模型。
     *
     * @param request 用户请求
     * @return 尚未执行的运行句柄
     */
    AgentRun prepare(AgentRequest request);

    /**
     * 准备、执行并阻塞等待最终结果；当前阶段没有超时限制。
     *
     * @param request 用户请求
     * @return 完整 Agent 结果，执行失败时状态为 FAILED
     */
    AgentResult run(AgentRequest request);
}
