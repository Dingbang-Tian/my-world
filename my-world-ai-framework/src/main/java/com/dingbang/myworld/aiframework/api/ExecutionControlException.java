package com.dingbang.myworld.aiframework.api;

/**
 * 表示取消、截止时间或预算引起的执行停止。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class ExecutionControlException extends RuntimeException {
    /** 可供调用方分类的稳定错误码。 */
    private final String code;

    /**
     * 创建执行控制异常。
     *
     * @param code 稳定错误码
     * @param message 停止原因
     */
    public ExecutionControlException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 返回停止类别。
     *
     * @return 稳定错误码
     */
    public String getCode() {
        return code;
    }
}
