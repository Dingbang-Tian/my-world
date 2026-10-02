package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 在调用时记录请求并执行本地脚本的 Agent 测试模型。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
final class ScriptedAgentModelGateway implements ModelGateway {

    /**

     * 按模型请求和事件监听器执行的本地脚本。

     */
    private final BiConsumer<ModelRequest, ModelEventListener> script;

    /**

     * 模型调用次数。

     */
    private final AtomicInteger callCount = new AtomicInteger();

    /**

     * 已执行的模型请求。

     */
    private final List<ModelRequest> requests = Collections.synchronizedList(new ArrayList<>());

    /**
     * 绑定本地脚本。
     *
     * @param script 按请求和监听器执行的模型脚本
     */
    ScriptedAgentModelGateway(BiConsumer<ModelRequest, ModelEventListener> script) {
        this.script = Objects.requireNonNull(script, "模型脚本不能为 null");
    }

    /**
     * 记录本次调用并执行脚本，脚本异常通过模型错误回调返回。
     *
     * @param request 本轮模型请求
     * @param listener 接收模型事件的监听器
     */
    @Override
    public void generate(ModelRequest request, ModelEventListener listener) {
        // 已校验的模型请求。
        ModelRequest actualRequest = Objects.requireNonNull(request, "模型请求不能为 null");
        // 已校验的模型事件监听器。
        ModelEventListener actualListener = Objects.requireNonNull(listener, "模型事件监听器不能为 null");
        callCount.incrementAndGet();
        requests.add(actualRequest);
        try {
            script.accept(actualRequest, actualListener);
        } catch (RuntimeException exception) {
            actualListener.onError(exception);
        }
    }

    /**
     * 获取已执行的模型调用次数。
     *
     * @return 模型调用次数
     */
    int getCallCount() {
        return callCount.get();
    }

    /**
     * 获取已执行请求的快照。
     *
     * @return 请求列表副本
     */
    List<ModelRequest> getRequests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }
}
