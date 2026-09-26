package com.dingbang.myworld.common.utils.collection;

import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * StreamEx 流操作增强工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public final class Streams {

    /**
     * 单元素的StreamEx
     *
     * @param element
     * @param <T>
     * @return
     */
    public static <T> StreamEx<T> of(T element) {
        if (element == null) {
            return StreamEx.empty();
        }
        return StreamEx.of(element);
    }

    /**
     * 多个元素的StreamEx
     *
     * @param elements
     * @param <T>
     * @return
     */
    @SafeVarargs
    public static <T> StreamEx<T> of(T... elements) {
        if (elements == null) {
            return StreamEx.empty();
        }
        return StreamEx.of(elements);
    }

    /**
     * 根据集合对象创建StreamEx
     *
     * @param collection
     * @param <T>
     * @return
     */
    public static <T> StreamEx<T> of(Collection<? extends T> collection) {
        if (collection == null) {
            return StreamEx.empty();
        }
        return StreamEx.of(collection);
    }

    /**
     * 将List转换为key为索引，value为值的EntryStream
     *
     * @param list
     * @param <V>
     * @return
     */
    public static <V> EntryStream<Integer, V> withIndex(List<V> list) {
        if (list == null) {
            return EntryStream.empty();
        }
        return EntryStream.of(list);
    }

    /**
     * 将Map的key转换成StreamEx
     *
     * @param map
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> StreamEx<K> ofKeys(Map<K, V> map) {
        if (map == null) {
            return StreamEx.empty();
        }
        return StreamEx.ofKeys(map);
    }

    /**
     * 将Map的value转换成StreamEx
     *
     * @param map
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> StreamEx<V> ofValues(Map<K, V> map) {
        if (map == null) {
            return StreamEx.empty();
        }
        return StreamEx.ofValues(map);
    }

    /**
     * 将Map转换为EntryStream
     *
     * @param map
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> EntryStream<K, V> of(Map<K, V> map) {
        if (map == null) {
            return EntryStream.empty();
        }
        return EntryStream.of(map);
    }

}
