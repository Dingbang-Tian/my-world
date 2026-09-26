package com.dingbang.myworld.common.utils.collection;

import cn.hutool.core.collection.IterUtil;
import lombok.NonNull;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.StreamSupport;

/**
 * 可迭代对象操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class IterableUtils extends IterUtil {

    /**
     * 遍历元素，并提供当前元素的索引值
     */
    public static <E> void forEach(Iterable<? extends E> elements, BiConsumer<Integer, ? super E> action) {
        Objects.requireNonNull(elements);
        Objects.requireNonNull(action);

        int index = 0;
        for (E element : elements) {
            action.accept(index++, element);
        }
    }

    /**
     * 遍历元素
     */
    public static <E> void forEachIfNotEmpty(Iterable<? extends E> elements, Consumer<? super E> action) {
        if (isEmpty(elements) || Objects.isNull(action)) {
            return;
        }

        elements.forEach(action);
    }

    /**
     * toArray
     */
    public static <A> A[] toArray(Iterable<? extends A> elements, @NonNull IntFunction<A[]> arrayTypes) {
        return toArray(elements, arrayTypes, Function.identity());
    }

    /**
     * toArray
     */
    public static <E,A> A[] toArray(Iterable<? extends E> elements, @NonNull IntFunction<A[]> arrayTypes, @NonNull Function<E,A> mapping) {
        if (isEmpty(elements)) {
            return arrayTypes.apply(0);
        }

        return StreamSupport.stream(elements.spliterator(), false).map(mapping).toArray(arrayTypes);
    }

}
