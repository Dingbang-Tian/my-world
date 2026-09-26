package com.dingbang.myworld.common.utils.lang;

import cn.hutool.core.util.EnumUtil;

import java.util.Arrays;
import java.util.function.Function;

/**
 * 枚举操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class EnumUtils extends EnumUtil {

    /**
     * 判断枚举是否包含 某字段对应的值
     *
     * @param enumClass 枚举类
     * @param fieldFunction 获取字段函数
     * @param value 值
     *
     * @return boolean
     */
    public static <E extends Enum<E>, T> boolean contains(Class<E> enumClass, Function<E, T> fieldFunction, T value) {
        return Arrays.stream(enumClass.getEnumConstants()).map(fieldFunction).anyMatch(e -> e.equals(value));
    }

}
