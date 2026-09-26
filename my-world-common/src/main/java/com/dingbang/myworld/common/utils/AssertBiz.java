package com.dingbang.myworld.common.utils;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.text.StrFormatter;
import com.dingbang.myworld.common.exception.BusinessException;
import com.dingbang.myworld.common.exception.ErrorCode;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.Objects;

/**
 * 业务条件断言工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class AssertBiz {
    public static void isTrue(boolean expression, ErrorCode errorEnum) {
        if (!expression) {
            throwNormalException(errorEnum);
        }
    }

    public static void isNull(Object obj, ErrorCode errorEnum, Object... params) {
        if (obj != null) {
            throwNormalException(errorEnum, params);
        }
    }

    public static void isNull(Object obj, ErrorCode errorEnum) {
        if (obj != null) {
            throwNormalException(errorEnum);
        }
    }

    public static void nonNull(Object obj, ErrorCode errorEnum, Object... params) {
        if (obj == null) {
            throwNormalException(errorEnum, params);
        }
    }

    public static void nonNull(Object obj, ErrorCode errorEnum) {
        if (obj == null) {
            throwNormalException(errorEnum);
        }
    }

    public static void isNotBlank(String str, ErrorCode errorEnum, Object... params) {
        if (StringUtils.isBlank(str)) {
            throwNormalException(errorEnum, params);
        }
    }

    public static void isNotBlank(String str, ErrorCode errorEnum) {
        if (StringUtils.isBlank(str)) {
            throwNormalException(errorEnum);
        }
    }


    public static void isNotEmpty(String str, ErrorCode errorEnum, Object... params) {
        if (StringUtils.isEmpty(str)) {
            throwNormalException(errorEnum, params);
        }
    }
    public static void isEmpty(Object value, ErrorCode errorEnum, Object... params) {
        if (ObjectUtil.isNotEmpty(value)) {
            throwNormalException(errorEnum, params);
        }
    }

    public static void isNotEmpty(String str, ErrorCode errorEnum) {
        if (StringUtils.isEmpty(str)) {
            throwNormalException(errorEnum);
        }
    }


    public static void isNotEmpty(Collection<?> collection, ErrorCode errorEnum, Object... params) {
        if (CollectionUtils.isEmpty(collection)) {
            throwNormalException(errorEnum, params);
        }
    }

    public static void isNotEmpty(Collection<?> collection, ErrorCode errorEnum) {
        if (CollectionUtils.isEmpty(collection)) {
            throwNormalException(errorEnum);
        }
    }

    /**
     * 判断是否有值==>cn.hutool.core.util.ObjectUtil#isNotEmpty(java.lang.Object)
     */
    public static <T> T hasValue(T obj, ErrorCode errorEnum, Object... args) {
        if (ObjectUtil.isEmpty(obj)) {
            throwNormalException(errorEnum, args);
        }
        return obj;
    }

    public static void isTrue(boolean obj, ErrorCode errorEnum, Object... args) {
        if (!obj) {
            throwNormalException(errorEnum, args);
        }
    }

    public static void throwNormalException(ErrorCode errorEnum, Object... args) {
        String message = errorEnum.getMessage();
        if (args != null && args.length > 0) {
            message = StrFormatter.format(message, args);
        }
        throw new BusinessException(errorEnum, message);
    }

    public static AssertThrow isTrue(boolean obj) {
        return () -> obj;
    }

    public static AssertThrow notTrue(boolean obj) {
        return () -> !obj;
    }

    public static AssertThrow isNull(Object obj) {
        return () -> Objects.isNull(obj);
    }

    public static AssertThrow nonNull(Object obj) {
        return () -> Objects.nonNull(obj);
    }

    public static AssertThrow isBlank(String str) {
        return () -> StringUtils.isBlank(str);
    }

    public static AssertThrow isNotBlank(String str) {
        return () -> StringUtils.isNotBlank(str);
    }

    public static AssertThrow isEmpty(Object obj) {
        return () -> ObjectUtils.isEmpty(obj);
    }

    public static AssertThrow isNotEmpty(Object obj) {
        return () -> !ObjectUtils.isEmpty(obj);
    }

    /**
     * 业务断言检查接口。
     *
     * @author Sebastian
     * @since 2026/09/25
     */
    public interface AssertThrow {

        /**
         * 校验
         * @return
         */
        boolean check();

        /**
         * 校验失败时抛出异常
         * @param errorEnum
         * @param params
         */
        default void elseThrow(ErrorCode errorEnum, Object... params) {
            if (!check()) {
                throwNormalException(errorEnum, params);
            }
        }

    }
}
