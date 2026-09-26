package com.dingbang.myworld.common.utils.collection;

import cn.hutool.core.map.MapUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 映射操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class MapUtils extends MapUtil {

    /**
     * map中是否含有key键
     */
    public static <K, V> boolean containsKey(Map<K, V> map, K key) {
        return Optional.ofNullable(map).map(m -> m.containsKey(key)).orElse(false);
    }

    public static <K, V> void putAll(Map<K, V> map, Map<K, V> subMap) {
        if (Objects.isNull(map) || Objects.isNull(subMap)) {
            return;
        }

        map.putAll(subMap);
    }

    /**
     * 通过key获取map的值
     * 默认值：null
     */
    public static <K, V> V getOrNull(Map<K, V> map, K key) {
        return getOrDefault(map, key, null);
    }

    /**
     * 通过key获取map的值
     * 默认值：空字符串
     */
    public static <K> String getOrEmpty(Map<K, String> map, K key) {
        return getOrDefault(map, key, StringUtils.EMPTY);
    }

    /**
     * 通过key获取map的值
     * 默认值：空列表
     */
    public static <K, T> List<T> getOrEmptyList(Map<K, List<T>> map, K key) {
        return getOrDefault(map, key, Lists.newArrayList());
    }

    /**
     * 通过key获取map的值
     * 默认值：空映射
     */
    public static <K, T, V> Map<T, V> getOrEmptyMap(Map<K, Map<T, V>> map, K key) {
        return getOrDefault(map, key, Maps.newHashMap());
    }

    /**
     * 通过key获取map的值
     * 默认值：通过defaultValue指定
     */
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (Objects.isNull(map)) {
            return defaultValue;
        }

        return Optional.ofNullable(map.get(key)).orElse(defaultValue);
    }

    /**
     * 通过key获取map的值对象下的字段数据
     * 默认值：null
     */
    public static <K, V, T> T getOrNull(Map<K, V> map, K key, Function<V, T> fieldMapper) {
        return getOrDefault(map, key, fieldMapper, null);
    }

    /**
     * 通过key获取map的值对象下的字段数据
     * 默认值：空字符串
     */
    public static <K, V> String getOrEmpty(Map<K, V> map, K key, Function<V, String> fieldMapper) {
        return getOrDefault(map, key, fieldMapper, StringUtils.EMPTY);
    }

    /**
     * 通过key获取map的值对象下的字段数据
     * 默认值：空列表
     */
    public static <K, V, T> List<T> getOrEmptyList(Map<K, V> map, K key, Function<V, List<T>> fieldMapper) {
        return getOrDefault(map, key, fieldMapper, Lists.newArrayList());
    }

    /**
     * 通过key获取map的值对象下的字段数据
     * 默认值：通过defaultValue指定
     */
    public static <K, V, T> T getOrDefault(Map<K, V> map, K key, Function<V, T> fieldMapper, T defaultValue) {
        if (Objects.isNull(map)) {
            return defaultValue;
        }

        return Optional.ofNullable(map.get(key)).map(fieldMapper).orElse(defaultValue);
    }

    /**
     * 获取按指定字段双层分组后的列表
     */
    public static <E, K, T> Map<K, Map<T, List<E>>> groupByToList(List<E> list, Function<E, K> groupMapper,
                                                                  Function<E, T> nextGroupMapper) {
        return groupByToList(list, groupMapper, nextGroupMapper, Function.identity());
    }

    /**
     * 获取按指定字段双层分组后的列表
     */
    public static <E, K, T, V> Map<K, Map<T, List<V>>> groupByToList(List<E> list, Function<E, K> groupMapper,
                                                         Function<E, T> nextGroupMapper, Function<E, V> valueMapper) {
        return groupByToList(list, groupMapper, nextGroupMapper, HashMap::new, valueMapper);
    }

    /**
     * 获取按指定字段双层分组后的列表
     */
    public static <E, K, T, V, M extends Map<T, List<V>>> Map<K, M> groupByToList(List<E> list, Function<E, K> groupMapper,
                                Function<E, T> nextGroupMapper, Supplier<M> mapSupplier, Function<E, V> valueMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }

        return list.stream().collect(Collectors.groupingBy(groupMapper, Collectors.groupingBy(nextGroupMapper, mapSupplier,
                Collectors.mapping(valueMapper, Collectors.toList()))));
    }

    /**
     * 获取按指定字段分组后的集合
     */
    public static <E, K, T> Map<K, Set<T>> groupByToSet(List<E> list, Function<E, K> groupMapper, Function<E, T> valueMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }

        return list.stream().collect(Collectors.groupingBy(groupMapper, Collectors.mapping(valueMapper, Collectors.toSet())));
    }

    /**
     * 获取按指定字段分组后扁平化拆分的列表
     */
    public static <E, K, T> Map<K, List<T>> groupByToFlatList(List<E> list, Function<E, K> groupMapper,
                                                              Function<E, List<T>> fieldListMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }

        return list.stream().collect(Collectors.groupingBy(groupMapper, Collectors.collectingAndThen(Collectors
                .mapping(fieldListMapper, Collectors.toList()), fieldList -> fieldList.stream().flatMap(Collection::stream)
                .collect(Collectors.toList()))));
    }

    /**
     * 获取按指定字段双层分组后的统计
     */
    public static <E, K, T> Map<K, Map<T, Long>> groupByToCount(List<E> list, Function<E, K> groupMapper,
                                                                Function<E, T> nextGroupMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }

        return list.stream().collect(Collectors.groupingBy(groupMapper, Collectors.groupingBy(nextGroupMapper,
                Collectors.counting())));
    }

    /**
     * 获取按指定字段双层分组后的映射
     */
    public static <E, K, T> Map<K, Map<T, E>> groupByToMap(List<E> list, Function<E, K> groupMapper,
                                                           Function<E, T> keyMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }

        return list.stream().collect(Collectors.groupingBy(groupMapper, Collectors.toMap(keyMapper, Function.identity(),
                (k1, k2) -> k1)));
    }

    /**
     * 获取按指定字段双层分组后的映射
     */
    public static <E, K, T, V> Map<K, Map<T, V>> groupByToMap(List<E> list, Function<E, K> groupMapper,
                                                              Function<E, T> keyMapper, Function<E, V> valueMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }

        return list.stream().collect(Collectors.groupingBy(groupMapper, Collectors.toMap(keyMapper, valueMapper,
                (k1, k2) -> k1)));
    }

    /**
     * 获取按指定字段双层分组后的集合
     */
    public static <E, K, T, V> Map<K, Map<T, Set<V>>> groupByToSet(List<E> list, Function<E, K> groupMapper,
                                                           Function<E, T> nextGroupMapper, Function<E, V> keyMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }

        return list.stream().collect(Collectors.groupingBy(groupMapper, Collectors.groupingBy(nextGroupMapper,
                Collectors.mapping(keyMapper, Collectors.toSet()))));
    }

    public static <K, V> V computeIfAbsent(Map<K, V> map, K key, Function<K, V> keyToValue, boolean onlyIfAbsent) {
        Objects.requireNonNull(map);
        Objects.requireNonNull(key);
        Objects.requireNonNull(keyToValue);

        if (map instanceof ConcurrentMap) {
            //try get
            V value = map.get(key);
            if (Objects.nonNull(value)) {
                return value;
            }
            V newValue = keyToValue.apply(key);
            if (Objects.nonNull(newValue)) {
                if (onlyIfAbsent) {
                    map.putIfAbsent(key, newValue);
                } else {
                    map.put(key, newValue);
                }
            }
            //re get
            return map.get(key);
        } else {
            return map.computeIfAbsent(key, keyToValue);
        }
    }

}
