package com.dingbang.myworld.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通用错误码
 *
 * @author dingbang.tian
 * @since 2026/09/21
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /**
     * 请求参数错误。
     */
    BAD_REQUEST("BAD_REQUEST", "请求参数错误"),
    /**
     * 系统内部错误。
     */
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误");

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误信息
     */
    private final String message;

}
