package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * 可开关地记录模型请求与流事件的脱敏调试元数据。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public final class SafeModelDebugGateway implements ModelGateway {
    /**
     * 调试元数据的日志入口。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeModelDebugGateway.class);
    /**
     * 实际执行模型请求的网关。
     */
    private final ModelGateway delegate;

    /**
     * 绑定模型网关；启用状态由可信进程配置决定。
     *
     * @param delegate 实际模型网关
     */
    public SafeModelDebugGateway(ModelGateway delegate) {
        this.delegate = Objects.requireNonNull(delegate, "模型网关不能为 null");
    }

    /**
     * 仅在进程显式启用调试时包装网关。
     *
     * @param delegate 实际模型网关
     * @return 开关控制后的网关
     */
    public static ModelGateway whenEnabled(ModelGateway delegate) {
        return Boolean.getBoolean("myworld.agent.debug") ? new SafeModelDebugGateway(delegate) : delegate;
    }

    /**
     * 转发请求和流事件，只记录类型、计数和稳定的调用关联标识。
     *
     * @param request 模型请求
     * @param listener 原始事件监听器
     */
    @Override
    public void generate(ModelRequest request, ModelEventListener listener) {
        Objects.requireNonNull(request, "模型请求不能为 null");
        Objects.requireNonNull(listener, "模型监听器不能为 null");
        // 本次请求与事件共享的调试关联标识。
        String callId = UUID.randomUUID().toString();
        LOGGER.debug("model_debug event=request callId={} modelId={} messageCount={} toolCount={} "
                        + "temperature={} maxCompletionTokens={} reasoningEffort={} content=[REDACTED]",
                callId, safe(request.getModelId()), request.getMessages().size(), request.getTools().size(),
                request.getOptions().getTemperature(), request.getOptions().getMaxCompletionTokens(),
                safe(request.getOptions().getReasoningEffort()));
        delegate.generate(request, new DebugModelEventListener(callId, listener));
    }

    /**
     * 限制配置标识长度和字符集，避免把任意文本写入日志。
     *
     * @param value 标识，可为空
     * @return 单行安全标识
     */
    private static String safe(String value) {
        if (value == null) return null;
        // 单行且仅含安全字符的标识。
        String sanitized = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 120));
    }
}
