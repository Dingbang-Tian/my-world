package com.dingbang.myworld.web.ai.codegen;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventException;
import com.dingbang.myworld.agent.api.AgentEventListener;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 Agent 事件转发到开发接口的 SSE 连接。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class CodegenSseListener implements AgentEventListener {
    /**
     * 当前 SSE 连接。
     */
    private final SseEmitter emitter;
    /**
     * 连接是否仍可接收事件。
     */
    private final AtomicBoolean open;

    /**
     * 发送包含序号、类型和载荷的事件。
     *
     * @param event 运行事件
     */
    @Override
    public void onEvent(AgentEvent event) {
        if (!open.get()) return;
        try {
            emitter.send(SseEmitter.event().id(Long.toString(event.getSequence()))
                    .name(event.getType().name()).data(event));
        } catch (IOException exception) {
            open.set(false);
        }
    }

    /**
     * 结束 SSE 连接。
     */
    @Override
    public void onComplete() {
        if (open.getAndSet(false)) emitter.complete();
    }

    /**
     * 将订阅错误传递给 SSE 连接。
     *
     * @param error 当前订阅错误
     */
    @Override
    public void onError(AgentEventException error) {
        if (open.getAndSet(false)) emitter.completeWithError(error);
    }
}
