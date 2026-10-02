package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import reactor.core.publisher.Flux;

/**
 * 协议中立的单次模型请求入口，不执行 Java 工具或 Agent 循环。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
public interface ModelGateway {

    /**
     * 创建冷事件流；每次订阅仅触发一次模型回合，调用方应只订阅一次。
     *
     * @param request 本轮模型请求快照
     * @return 文本增量与唯一完整回合组成的事件流，失败走流错误通道
     */
    Flux<ModelEvent> generate(ModelRequest request);
}
