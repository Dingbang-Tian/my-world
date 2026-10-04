package com.dingbang.myworld.common.utils.lang;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 通用对象构建器。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@RequiredArgsConstructor
public class Builder<T> {

    /**
     * 待构造对象的提供器。
     */
    private final Supplier<T> instanceSupplier;

    /**
     * 构造对象时依次执行的设置动作。
     */
    private final List<Consumer<T>> setters = Lists.newArrayList();

    public static <T> Builder<T> of(Supplier<T> instanceSupplier) {
        return new Builder<>(instanceSupplier);
    }

    public <P> Builder<T> with(BiConsumer<T, P> setConsumer, P value) {
        Consumer<T> setter = instance -> setConsumer.accept(instance, value);
        setters.add(setter);

        return this;
    }

    public T build() {
        T instance = instanceSupplier.get();
        setters.forEach(setter -> setter.accept(instance));
        setters.clear();

        return instance;
    }

}
