package com.dingbang.myworld.common.utils.lang;

import cn.hutool.core.util.ArrayUtil;

/**
 * 数组操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class ArrayUtils extends ArrayUtil {

    /**
     * 获取第一个元素的值
     *     默认值：null
     */
    public static <T> T getFirst(T[] array) {
        return get(array, 0);
    }

}
