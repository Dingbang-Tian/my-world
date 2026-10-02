package com.dingbang.myworld.aiframework.api.event;

import com.dingbang.myworld.aiframework.api.event.eventImpl.TurnCompleted;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 校验模型事件流必须以唯一完整回合正常结束。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
public final class ModelEventStreams {

    /**
     * 阻止工具类被实例化。
     */
    private ModelEventStreams() {
    }

    /**
     * 为每次订阅校验唯一终态；源流错误保持为错误，缺失或重复终态报错。
     *
     * @param source 模型适配器产生的事件流
     * @return 带单次订阅终态校验的冷事件流
     * @throws NullPointerException 当源流为 null 时
     */
    public static Flux<ModelEvent> requireCompleted(Flux<ModelEvent> source) {
        Objects.requireNonNull(source, "模型事件流不能为 null");
        return Flux.defer(() -> {
            // 当前订阅是否已经收到完整回合。
            AtomicBoolean completed = new AtomicBoolean();
            return source.<ModelEvent>handle((event, sink) -> {
                if (completed.get()) {
                    sink.error(new IllegalStateException("完整回合之后不能继续发出事件"));
                    return;
                }
                if (event instanceof TurnCompleted) {
                    completed.set(true);
                }
                sink.next(event);
            }).concatWith(Mono.defer(() -> completed.get()
                    ? Mono.empty()
                    : Mono.error(new IllegalStateException("模型事件流缺少完整回合"))));
        });
    }
}
