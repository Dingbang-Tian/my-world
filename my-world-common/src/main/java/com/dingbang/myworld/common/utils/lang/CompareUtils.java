package com.dingbang.myworld.common.utils.lang;

import cn.hutool.core.comparator.CompareUtil;

import static cn.hutool.core.util.BooleanUtil.toInt;
import static java.lang.Integer.signum;

/**
 * 对象比较工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class CompareUtils extends CompareUtil {

    /**
     * 判断给定值是否处于两值之间
     *
     * @param min          最小值
     * @param middle       中间值
     * @param max          最大值
     * @param isIncludeMin 是否包含最小值
     * @param isIncludeMax 是否包含最大值
     * @param <T>
     * @return
     */
    public static <T extends Comparable<? super T>> boolean between(T min, T middle, T max, boolean isIncludeMin, boolean isIncludeMax) {
        if (min == null || middle == null || max == null) {
            return false;
        }
        return signum(min.compareTo(middle)) < toInt(isIncludeMin) && signum(middle.compareTo(max)) < toInt(isIncludeMax);
    }

    /**
     * 判断给定值是否处于两值之间
     *
     * @param minInclude 最小值（包含）
     * @param mid        中间值
     * @param maxInclude 最大值（包含）
     * @return boolean
     */
    public static <T extends Comparable<? super T>> boolean between(T minInclude, T mid, T maxInclude) {
        return between(minInclude, mid, maxInclude, true, true);
    }

    /**
     * 是否相等
     *
     * @param firstValue
     * @param secondValue
     * @return boolean
     */
    public static <T extends Comparable<? super T>> boolean equals(T firstValue, T secondValue) {
        if (firstValue == secondValue) {
            return true;
        }

        if (firstValue == null || secondValue == null) {
            return false;
        }

        return firstValue.compareTo(secondValue) == 0;
    }

}
