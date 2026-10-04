package com.dingbang.myworld.aiframework.protocol.openai;

import com.dingbang.myworld.aiframework.api.ModelGatewayException;
import lombok.Getter;

/**
 * 表示 Chat 协议响应状态或流内容无效的异常。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Getter
public final class OpenAiChatProtocolException extends ModelGatewayException {

    /**
     * HTTP 状态码，协议解析错误时为零。
     */
    private final int httpStatus;

    /**
     * 构造不包含服务端敏感响应体的协议异常。
     *
     * @param message 可安全展示的错误说明
     * @param httpStatus HTTP 状态码，解析错误时为零
     */
    public OpenAiChatProtocolException(String message, int httpStatus) {
        super(codeForStatus(httpStatus), message);
        this.httpStatus = httpStatus;
    }

    /**
     * 将本地请求/凭据问题与服务端或流协议问题分开。
     *
     * @param httpStatus HTTP 状态码，协议解析错误为零
     * @return 稳定错误码
     */
    private static String codeForStatus(int httpStatus) {
        if (httpStatus == 400 || httpStatus == 422) {
            return "INVALID_REQUEST";
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return "CONFIGURATION_ERROR";
        }
        return "MODEL_ERROR";
    }

}
