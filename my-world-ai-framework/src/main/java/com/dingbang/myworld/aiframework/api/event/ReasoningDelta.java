package com.dingbang.myworld.aiframework.api.event;

import lombok.Data;

import java.util.Objects;

/**
 * 供应商提供的推理文本增量。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class ReasoningDelta implements ModelEvent {

    /**
     * 本次推理文本增量。
     */
    private final String text;

    /**
     * 固定推理文本。
     *
     * @param text 增量文本
     */
    public ReasoningDelta(String text) {
        this.text = Objects.requireNonNull(text, "推理文本不能为 null");
    }
}
