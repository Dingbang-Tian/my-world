package com.dingbang.myworld.common.utils.lang;

import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 字符串操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class StringUtils extends StrUtil {

    /**
     * 获取指定字段拼接后的字符串
     * 默认：逗号分隔符
     *
     * @return String
     */
    public static <E> String joinMapBy(List<E> list, Function<E, String> fieldMapper) {
        return joinMapBy(list, fieldMapper, COMMA);
    }

    /**
     * 获取指定字段拼接后的字符串
     *
     * @return String
     */
    public static <E> String joinMapBy(List<E> list, Function<E, String> fieldMapper, String delimiter) {
        return joinMapBy(list, fieldMapper, delimiter, EMPTY, EMPTY);
    }

    /**
     * 获取指定字段拼接后的字符串
     *
     * @return String
     */
    public static <E> String joinMapBy(List<E> list, Function<E, String> fieldMapper, String delimiter, String prefix, String suffix) {
        if (CollectionUtils.isEmpty(list)) {
            return EMPTY;
        }

        return list.stream().map(fieldMapper).collect(Collectors.joining(delimiter, prefix, suffix));
    }

    public static List<String> split(String input) {
        return split(input, Function.identity());
    }

    public static <E> List<E> split(String input, @NonNull Function<String, E> mapping) {
        return split(input, mapping, Lists::newArrayList);
    }

    public static <E, C extends Collection<E>> C split(String text, @NonNull Function<String, E> mapper, @NonNull Supplier<C> supplier) {
        if (isEmpty(text)) {
            return supplier.get();
        }
        return Arrays.stream(text.split(COMMA)).filter(StringUtils::isNotEmpty).map(mapper).filter(Objects::nonNull).collect(Collectors.toCollection(supplier));
    }
}
