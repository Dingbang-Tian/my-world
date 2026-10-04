package com.dingbang.myworld.aiframework.api.event;

import com.dingbang.myworld.aiframework.api.ModelTokenUsage;
import lombok.Data;

import java.util.Objects;

/**
 * 模型在流尾报告的最终 token 用量。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class UsageReported implements ModelEvent {

    /**
     * 本次模型调用的最终用量。
     */
    private final ModelTokenUsage usage;

    /**
     * 固定用量事件。
     *
     * @param usage 完整 token 用量
     */
    public UsageReported(ModelTokenUsage usage) {
        this.usage = Objects.requireNonNull(usage, "模型用量不能为 null");
    }
}
