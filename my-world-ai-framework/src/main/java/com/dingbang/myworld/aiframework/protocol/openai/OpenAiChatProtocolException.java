package com.dingbang.myworld.aiframework.protocol.openai;

/**
 * 表示 Chat 协议响应状态或流内容无效的异常。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class OpenAiChatProtocolException extends RuntimeException {

    /** HTTP 状态码，协议解析错误时为零。 */
    private final int httpStatus;

    /**
     * 构造不包含服务端敏感响应体的协议异常。
     *
     * @param message 可安全展示的错误说明
     * @param httpStatus HTTP 状态码，解析错误时为零
     */
    public OpenAiChatProtocolException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * 返回 HTTP 状态码。
     *
     * @return HTTP 状态码，解析错误时为零
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}
