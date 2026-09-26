package com.dingbang.myworld.common.utils.funciton;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 函数式类型转换工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class Converters {

    /**
     * ignore null value
     */
    public static <T, E> Set<E> toSet(Collection<T> items, Function<T, E> mapper) {
        if (CollectionUtils.isEmpty(items)) {
            return Sets.newHashSet();
        }

        return items.stream().filter(Objects::nonNull).map(mapper).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * ignore null value
     */
    public static <T, E extends Comparable<? super E>> TreeSet<E> toTreeSet(Collection<T> items, Function<T, E> mapper) {
        if (CollectionUtils.isEmpty(items)) {
            return Sets.newTreeSet();
        }

        return items.stream().filter(Objects::nonNull).map(mapper).filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * ignore null value
     */
    public static <T, E> List<E> toList(Collection<T> items, Function<T, E> mapper) {
        if (CollectionUtils.isEmpty(items)) {
            return Lists.newArrayList();
        }

        return items.stream().filter(Objects::nonNull).map(mapper).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 1、ignore null key & value
     * 2、keep old value on duplicate
     */
    public static <T, K> Map<K, T> toMap(Collection<T> items, Function<T, K> keyMapper) {
        return toMap(items, keyMapper, Function.identity(), (o, n) -> o);
    }

    public static <T, K, V> Map<K, V> toMap(Collection<T> items, Function<T, K> keyMapper, Function<T, V> valueMapper) {
        return toMap(items, keyMapper, valueMapper, (o, n) -> o);
    }

    public static <T, K, V> Map<K, V> toMap(Collection<T> items, Function<T, K> keyMapper, Function<T, V> valueMapper, BinaryOperator<V> merge) {
        return toMap(items, keyMapper, valueMapper, merge, HashMap::new);
    }

    public static <T, K, V, M extends Map<K, V>> Map<K, V> toMap(Collection<T> items, Function<T, K> keyMapper, Function<T, V> valueMapper, Supplier<M> mapSupplier) {
        return toMap(items, keyMapper, valueMapper, (o, n) -> o, mapSupplier);
    }

    public static <T, K, V, M extends Map<K, V>> Map<K, V> toMap(Collection<T> items, Function<T, K> keyMapper, Function<T, V> valueMapper, BinaryOperator<V> merge, Supplier<M> mapSupplier) {
        if (CollectionUtils.isEmpty(items)) {
            return Maps.newHashMap();
        }

        return items.stream()
            .filter(item -> Objects.nonNull(item) && Objects.nonNull(keyMapper.apply(item)) && Objects.nonNull(valueMapper.apply(item)))
            .collect(Collectors.toMap(keyMapper, valueMapper, merge, mapSupplier));
    }

    public static <T, K> Map<K, List<T>> group(Collection<T> items, Function<T, K> groupKey) {
        return group(items, groupKey, Function.identity(), ArrayList::new);
    }

    public static <T, K, V> Map<K, List<V>> group(Collection<T> items, Function<T, K> groupKey, Function<T, V> mappingKey) {
        return group(items, groupKey, mappingKey, ArrayList::new);
    }

    public static <T, K, V, C extends Collection<V>> Map<K, C> group(Collection<T> items, Function<T, K> groupKey,
        Function<T, V> mappingKey, Supplier<C> supplier) {
        if (CollectionUtils.isEmpty(items)) {
            return Maps.newHashMap();
        }

        return items.stream()
            .filter(item -> Objects.nonNull(item) && Objects.nonNull(groupKey.apply(item)) && Objects.nonNull(mappingKey.apply(item)))
            .collect(Collectors.groupingBy(groupKey, Collectors.mapping(mappingKey, Collectors.toCollection(supplier))));
    }

}
