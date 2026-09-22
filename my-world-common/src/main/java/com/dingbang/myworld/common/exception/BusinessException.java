package com.dingbang.myworld.common.exception;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author dingbang.tian
 * @since 2026/09/21
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 序列化版本
     */
    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码
     */
    private final ErrorCode errorCode;

    /**
     * 使用错误码创建业务异常
     *
     * @param errorCode
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码和自定义信息创建业务异常
     *
     * @param errorCode
     * @param message
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
