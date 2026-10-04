package com.dingbang.myworld.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一接口响应
 *
 * @author dingbang.tian
 * @since 2026/09/21
 */
@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * 响应状态码。
     */
    private final String code;

    /**
     * 面向调用方的响应说明。
     */
    private final String message;

    /**
     * 业务响应数据。
     */
    private final T data;

    /**
     * 成功响应码
     */
    private static final String SUCCESS_CODE = "SUCCESS";

    /**
     * 成功响应信息
     */
    private static final String SUCCESS_MESSAGE = "success";

    /**
     * 构造成功响应
     *
     * @param data 响应数据
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 构造失败响应
     *
     * @param code 失败状态码
     * @param message 失败原因
     * @return 失败响应
     */
    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
