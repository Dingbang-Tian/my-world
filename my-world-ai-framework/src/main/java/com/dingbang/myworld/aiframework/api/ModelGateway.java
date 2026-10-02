package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.aiframework.api.event.ModelEventListener;

/**
 * 协议中立的单次模型请求入口，不执行 Java 工具或 Agent 循环。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
public interface ModelGateway {

    /**
     * 发起一次模型调用，并通过监听器接收增量、错误或完成通知。
     *
     * @param request 本轮模型请求快照
     * @param listener 接收文本增量、唯一完整回合和终态通知的监听器
     */
    void generate(ModelRequest request, ModelEventListener listener);
}
