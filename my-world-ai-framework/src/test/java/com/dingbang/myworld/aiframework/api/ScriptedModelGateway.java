package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.aiframework.api.event.ModelEventListener;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 仅供测试使用的单次请求脚本模型，不连接外部服务。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
final class ScriptedModelGateway implements ModelGateway {

    /**

     * 根据本轮请求和事件监听器执行的测试脚本。

     */
    private final BiConsumer<ModelRequest, ModelEventListener> script;

    /**

     * 已触发脚本的模型调用次数。

     */
    private final AtomicInteger callCount = new AtomicInteger();

    /**
     * 绑定本地测试脚本。
     *
     * @param script 按请求和监听器执行的测试脚本
     */
    ScriptedModelGateway(BiConsumer<ModelRequest, ModelEventListener> script) {
        this.script = Objects.requireNonNull(script, "测试脚本不能为 null");
    }

    /**
     * 立即记录调用并执行测试脚本，脚本异常通过监听器返回。
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
        try {
            script.accept(actualRequest, actualListener);
        } catch (RuntimeException exception) {
            actualListener.onError(exception);
        }
    }

    /**
     * 获取已触发的模型调用次数。
     *
     * @return 模型调用次数
     */
    int getCallCount() {
        return callCount.get();
    }
}
