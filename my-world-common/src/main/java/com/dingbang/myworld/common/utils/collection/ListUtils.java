package com.dingbang.myworld.common.utils.collection;

import cn.hutool.core.collection.ListUtil;

import java.util.Comparator;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 列表操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class ListUtils extends ListUtil {

    /**
     * 获取第一个元素的值
     *     默认值：null
     */
    public static <E> E getFirst(List<E> list) {
        return getOrDefault(list, 0, null);
    }

    /**
     * 获取第一个元素的值
     *     默认值：defaultValue
     */
    public static <E> E getFirst(List<E> list, E defaultValue) {
        return getOrDefault(list, 0, defaultValue);
    }

    /**
     * 获取索引所在的值
     *     默认值：null
     */
    public static <E> E getOrNull(List<E> list, int index) {
        return getOrDefault(list, index, null);
    }

    /**
     * 获取索引所在的值
     *     默认值：defaultValue
     */
    public static <E> E getOrDefault(List<E> list, int index, E defaultValue) {
        if (index < 0 || CollectionUtils.isEmpty(list)) {
            return defaultValue;
        }

        return index < list.size() ? list.get(index) : defaultValue;
    }

    /**
     * 列表为 null 时返回空列表
     */
    public static <E> List<E> emptyListIfNull(List<E> list) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list;
    }

    /**
     * 获取去重后的列表
     */
    public static <E> List<E> distinct(List<E> list) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 获取指定字段去重后的列表
     */
    public static <E, T> List<T> distinctMapBy(List<E> list, Function<E, T> fieldMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().map(fieldMapper).distinct().collect(Collectors.toList());
    }

    /**
     * 获取指定字段的列表
     */
    public static <E, T> List<T> mapBy(List<E> list, Function<E, T> fieldMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().map(fieldMapper).collect(Collectors.toList());
    }

    /**
     * 列表遍历
     */
    public static <E> void forEach(List<E> list, Consumer<E> consumer) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }

        list.forEach(consumer);
    }

    /**
     * 获取指定字段扁平化拆分的列表
     */
    public static <E, T> List<T> flatMapBy(List<E> list, Function<E, Stream<T>> fieldMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().flatMap(fieldMapper).collect(Collectors.toList());
    }

    /**
     * 并行流获取指定字段的列表
     */
    public static <E, T> List<T> parallelMapBy(List<E> list, Function<E, T> fieldMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.parallelStream().map(fieldMapper).collect(Collectors.toList());
    }

    /**
     * 列表并行流遍历
     */
    public static <E> void parallelForEach(List<E> list, Consumer<E> consumer) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }

        list.parallelStream().forEach(consumer);
    }

    /**
     * 获取指定字段按指定条件过滤后的列表
     */
    public static <E, T> List<T> mapFilterBy(List<E> list, Function<E, T> fieldMapper, Predicate<T> filter) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().map(fieldMapper).filter(filter).collect(Collectors.toList());
    }

    /**
     * 获取指定字段按指定条件过滤后扁平化拆分的列表
     */
    public static <E, T> List<T> flatMapFilterBy(List<E> list, Function<E, Stream<T>> fieldMapper, Predicate<T> filter) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().flatMap(fieldMapper).filter(filter).collect(Collectors.toList());
    }

    /**
     * 并行流获取指定字段按指定条件过滤后的列表
     */
    public static <E, T> List<T> parallelMapFilterBy(List<E> list, Function<E, T> fieldMapper, Predicate<T> filter) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.parallelStream().map(fieldMapper).filter(filter).collect(Collectors.toList());
    }

    /**
     * 获取指定条件过滤后的列表
     */
    public static <E> List<E> filterBy(List<E> list, Predicate<E> filter) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().filter(filter).collect(Collectors.toList());
    }

    /**
     * 获取指定条件过滤后指定字段的列表
     */
    public static <E, T> List<T> filterMapBy(List<E> list, Predicate<E> filter, Function<E, T> fieldMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().filter(filter).map(fieldMapper).collect(Collectors.toList());
    }

    /**
     * 获取指定条件过滤后指定字段扁平化拆分的列表
     */
    public static <E, T> List<T> filterFlatMapBy(List<E> list, Predicate<E> filter, Function<E, Stream<T>> fieldMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().filter(filter).flatMap(fieldMapper).collect(Collectors.toList());
    }

    /**
     * 获取指定条件过滤去重后的列表
     */
    public static <E> List<E> distinctFilterBy(List<E> list, Predicate<E> filter) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().filter(filter).distinct().collect(Collectors.toList());
    }

    /**
     * 获取指定条件过滤后指定字段去重的列表
     */
    public static <E, T> List<T> distinctFilterMapBy(List<E> list, Predicate<E> filter, Function<E, T> fieldMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().filter(filter).map(fieldMapper).distinct().collect(Collectors.toList());
    }

    /**
     * 获取指定条件过滤后指定字段扁平化拆分去重的列表
     */
    public static <E, T> List<T> distinctFilterFlatMapBy(List<E> list, Predicate<E> filter, Function<E, Stream<T>> fieldMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().filter(filter).flatMap(fieldMapper).distinct().collect(Collectors.toList());
    }

    /**
     * 获取指定字段按指定条件过滤后去重的列表
     */
    public static <E, T> List<T> distinctMapFilterBy(List<E> list, Function<E, T> fieldMapper, Predicate<T> filter) {
        if (CollectionUtils.isEmpty(list)) {
            return toList();
        }

        return list.stream().map(fieldMapper).filter(filter).distinct().collect(Collectors.toList());
    }

    /**
     * 获取指定字段按指定计算器计算后的结果
     */
    public static <E, T> T mapReduceBy(List<E> list, Function<E, T> fieldMapper, BinaryOperator<T> accumulator) {
        return mapReduceBy(list, fieldMapper, accumulator, null);
    }

    /**
     * 获取指定字段按指定计算器计算后的结果
     */
    public static <E, T> T mapReduceBy(List<E> list, Function<E, T> fieldMapper, BinaryOperator<T> accumulator, T other) {
        if (CollectionUtils.isEmpty(list)) {
            return other;
        }

        return list.stream().map(fieldMapper).reduce(accumulator).orElse(other);
    }

    /**
     * 获取指定字段按指定计算器计算后的结果
     */
    public static <E, T> T mapReduceBy(List<E> list, Function<E, T> fieldMapper, T identity, BinaryOperator<T> accumulator) {
        if (CollectionUtils.isEmpty(list)) {
            return identity;
        }

        return list.stream().map(fieldMapper).reduce(identity, accumulator);
    }

    /**
     * 获取任一指定匹配的结果
     */
    public static <E> boolean anyMatch(List<E> list, Predicate<E> matchMapper) {
        if (CollectionUtils.isEmpty(list)) {
            return false;
        }

        return list.stream().anyMatch(matchMapper);
    }

    /**
     * 按指定比较器获取最大值
     */
    public static <E> E max(List<E> list, Comparator<E> comparator) {
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }

        return list.stream().max(comparator).orElse(null);
    }

}
