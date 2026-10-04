package com.dingbang.myworld.aiapp.codegen;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventListener;

import java.util.List;

/**
 * 将代码生成测试的运行事件收集到列表。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class CollectingAgentEventListener implements AgentEventListener {
    /**
     * 保存收到的运行事件。
     */
    private final List<AgentEvent> events;

    /**
     * 绑定事件列表。
     *
     * @param events 事件列表
     */
    CollectingAgentEventListener(List<AgentEvent> events) {
        this.events = events;
    }

    /**
     * 收集一条运行事件。
     *
     * @param event 当前事件
     */
    @Override
    public void onEvent(AgentEvent event) {
        events.add(event);
    }

    /**
     * 接收正常结束通知。
     */
    @Override
    public void onComplete() { }
}
