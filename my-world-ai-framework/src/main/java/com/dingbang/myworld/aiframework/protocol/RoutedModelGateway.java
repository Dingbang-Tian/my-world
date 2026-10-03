package com.dingbang.myworld.aiframework.protocol;

import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelGatewayException;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 按本地 modelId 将单轮请求交给已注册的协议适配器。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class RoutedModelGateway implements ModelGateway {
    /** 不可变的模型到适配器映射。 */
    private final Map<String, ModelGateway> routes;

    /**
     * 固定路由且拒绝空映射。
     *
     * @param routes 每个 modelId 对应的单轮适配器
     */
    public RoutedModelGateway(Map<String, ModelGateway> routes) {
        if (routes == null || routes.isEmpty()) throw new IllegalArgumentException("模型路由不能为空");
        routes.forEach((id, gateway) -> {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("模型标识不能为空");
            Objects.requireNonNull(gateway, "适配器不能为空");
        });
        this.routes = Map.copyOf(new LinkedHashMap<>(routes));
    }

    /**
     * 只把请求发送到 modelId 指定的适配器。
     *
     * @param request 模型请求
     * @param listener 事件监听器
     */
    @Override public void generate(ModelRequest request, ModelEventListener listener) {
        Objects.requireNonNull(request, "请求不能为空");
        Objects.requireNonNull(listener, "监听器不能为空");
        /** 选中的协议适配器。 */
        ModelGateway gateway = routes.get(request.getModelId());
        if (gateway == null) {
            listener.onError(new ModelGatewayException("CONFIGURATION_ERROR", "未知聊天模型: " + request.getModelId()));
            return;
        }
        gateway.generate(request, listener);
    }
}
