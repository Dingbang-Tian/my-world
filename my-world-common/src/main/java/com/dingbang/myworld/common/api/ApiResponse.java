package com.dingbang.myworld.common.api;

/**
 * 统一接口响应
 *
 * @author dingbang.tian
 * @since 2026/09/21
 */
public record ApiResponse<T>(String code, String message, T data) {

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
     * @param data
     * @return apiResponse
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 构造失败响应
     *
     * @param code
     * @param message
     * @return apiResponse
     */
    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
