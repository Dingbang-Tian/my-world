package com.dingbang.myworld.aiframework.api;

/**
 * 表示模型配置或能力在联网前无法满足请求。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public class ModelGatewayException extends RuntimeException {
    /** 可供 Agent 区分配置与能力错误的代码。 */
    private final String code;

    /**
     * 创建可识别的模型配置错误。
     *
     * @param code 错误码
     * @param message 错误说明
     */
    public ModelGatewayException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 返回错误码。
     *
     * @return 稳定错误码
     */
    public String getCode() {
        return code;
    }
}
