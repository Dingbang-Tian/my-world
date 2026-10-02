package com.dingbang.myworld.aiframework.api;

import lombok.Data;
import java.time.Instant;
import java.util.Objects;

/**
 * 单次模型调用共享的截止时间、取消信号与响应内存预算。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class ModelExecutionContext {
    /** 全局截止时间；直接调用网关可为空。 */
    private final Instant deadline;
    /** 与调用方共享的取消令牌。 */
    private final CancellationToken cancellation;
    /** 本次响应的最大 UTF-16 字符数，包含文本、推理和工具参数。 */
    private final int maxOutputCharacters;

    /**
     * 固定本次调用的执行边界。
     *
     * @param deadline 全局截止时间，可为空
     * @param cancellation 取消令牌
     * @param maxOutputCharacters 最大响应字符数，必须大于零
     */
    public ModelExecutionContext(Instant deadline, CancellationToken cancellation, int maxOutputCharacters) {
        if (maxOutputCharacters < 1) {
            throw new IllegalArgumentException("模型输出上限必须大于零");
        }
        this.deadline = deadline;
        this.cancellation = Objects.requireNonNull(cancellation, "取消令牌不能为 null");
        this.maxOutputCharacters = maxOutputCharacters;
    }

    /**
     * 创建独立网关调用的默认保护边界。
     *
     * @return 默认上下文
     */
    public static ModelExecutionContext defaults() {
        return new ModelExecutionContext(null, new CancellationToken(), 65536);
    }

    /**
     * 检查调用是否仍可继续。
     *
     * @throws ExecutionControlException 已取消或超过截止时间时
     */
    public void checkActive() {
        cancellation.checkCancelled();
        if (deadline != null && !Instant.now().isBefore(deadline)) {
            throw new ExecutionControlException("TIMEOUT", "模型调用超过全局截止时间");
        }
    }
}
