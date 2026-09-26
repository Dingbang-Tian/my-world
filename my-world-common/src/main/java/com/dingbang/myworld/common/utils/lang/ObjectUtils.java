package com.dingbang.myworld.common.utils.lang;

import cn.hutool.core.util.ObjectUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 对象操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class ObjectUtils extends ObjectUtil {

    /**
     * 将Object转为String
     * 策略为：
     * <pre>
     *  1、null转为""
     *  2、调用Convert.toStr(Object)转换
     * </pre>
     */
    public static <T> String nullToEmptyString(T object) {
        if (Objects.isNull(object)) {
            return StringUtils.EMPTY;
        }

        return toString(object);
    }

    /**
     * 获取object对象数据
     *     默认值：空字符串
     */
    public static String getOrEmpty(String object) {
        return getOrDefault(object, StringUtils.EMPTY);
    }

    /**
     * 获取object对象下的字段数据
     *     默认值：null
     */
    public static <T, V> V getOrNull(T object, Function<T, V> fieldMapper) {
        return getOrDefault(object, fieldMapper, null);
    }

    /**
     * 获取object对象下的字段数据
     *  默认值：空字符串
     */
    public static <T> String getOrEmpty(T object, Function<T, String> fieldMapper) {
        return getOrDefault(object, fieldMapper, StringUtils.EMPTY);
    }

    /**
     * 获取object对象数据
     *  默认值：defaultValue
     */
    public static <T> T getOrDefault(T object, T defaultValue) {
        return Optional.ofNullable(object).orElse(defaultValue);
    }

    /**
     * 获取object对象下的字段数据
     *  默认值：defaultValue
     */
    public static <T, V> V getOrDefault(T object, Function<T, V> fieldMapper, V defaultValue) {
        return Optional.ofNullable(object).map(fieldMapper).orElse(defaultValue);
    }

    /**
     * 非空时执行逻辑
     *
     * @param object 数据
     * @param consumer 逻辑处理
     * @return V
     */
    public static <T> T acceptIfNotEmpty(T object, Consumer<T> consumer) {
        if (isNotEmpty(object) && isNotNull(consumer)) {
            consumer.accept(object);
        }

        return object;
    }

    /**
     * 非空时执行逻辑
     *
     * @param object 数据
     * @param function 逻辑处理
     * @return V
     */
    public static <T, V> V applyIfNotEmpty(T object, Function<T, V> function) {
        if (isNotEmpty(object) && isNotNull(function)) {
            return function.apply(object);
        }

        return null;
    }

    /**
     * 获取任一指定匹配的结果
     *
     * @param object 数据
     * @param values 值
     * @return boolean
     */
    public static <T> boolean anyMatch(T object, T... values) {
        return ArrayUtils.contains(values, object);
    }

}
