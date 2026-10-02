package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 仅供测试使用的单次请求脚本模型，不连接外部服务。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
final class ScriptedModelGateway implements ModelGateway {

    /**
     * 根据本轮请求产生事件的测试脚本。
     */
    private final Function<ModelRequest, Flux<ModelEvent>> script;

    /**
     * 已触发脚本的订阅次数。
     */
    private final AtomicInteger subscriptionCount = new AtomicInteger();

    /**
     * 绑定本地测试脚本。
     *
     * @param script 按请求返回事件流的脚本
     */
    ScriptedModelGateway(Function<ModelRequest, Flux<ModelEvent>> script) {
        this.script = Objects.requireNonNull(script, "测试脚本不能为 null");
    }

    /**
     * 创建冷事件流，在每次订阅时恰好调用一次测试脚本。
     *
     * @param request 本轮模型请求
     * @return 脚本生成的事件流
     */
    @Override
    public Flux<ModelEvent> generate(ModelRequest request) {
        Objects.requireNonNull(request, "模型请求不能为 null");
        return Flux.defer(() -> {
            subscriptionCount.incrementAndGet();
            return Objects.requireNonNull(script.apply(request), "脚本事件流不能为 null");
        });
    }

    /**
     * 读取已触发脚本的订阅次数。
     *
     * @return 订阅次数
     */
    int subscriptionCount() {
        return subscriptionCount.get();
    }
}
