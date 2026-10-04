package com.dingbang.myworld.common.utils;

import com.dingbang.myworld.common.exception.ErrorCode;

/**
 * 业务断言检查接口。
 *
 * @author Sebastian
 * @since 2026/10/04
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
            AssertBiz.throwNormalException(errorEnum, params);
        }
    }

}
