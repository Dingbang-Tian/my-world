package com.dingbang.myworld.common.utils.funciton;

import lombok.NonNull;
import lombok.SneakyThrows;

/**
 * 支持抛出受检异常的调用接口。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@FunctionalInterface
public interface CheckedCallable<T> {

    /**
     * 执行任务
     *
     * @return t
     * @throws Throwable
     */
    T execute() throws Throwable;

    /**
     * SneakyThrows
     *
     * @param supplier
     * @param <V>
     * @return
     */
    @SneakyThrows
    static <V> V sneaky(@NonNull CheckedCallable<V> supplier) {
        return supplier.execute();
    }
}
