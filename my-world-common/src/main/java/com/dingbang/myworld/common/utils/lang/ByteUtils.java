package com.dingbang.myworld.common.utils.lang;

import cn.hutool.core.util.ByteUtil;
import org.apache.commons.lang3.BooleanUtils;

/**
 * 字节操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class ByteUtils extends ByteUtil {

    /**
     * 第N位是否为1
     *
     * @param digit 数字
     * @param position 位置
     * @return boolean
     */
    public static boolean isTrueOnPosition(int digit, int position) {
        return BooleanUtils.toBoolean((digit >> (position - 1)) & 1);
    }

}
