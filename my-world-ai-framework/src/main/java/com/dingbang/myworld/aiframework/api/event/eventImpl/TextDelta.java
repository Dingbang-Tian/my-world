package com.dingbang.myworld.aiframework.api.event.eventImpl;

import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import lombok.Data;

import java.util.Objects;

/**
 * 供实时展示的文本增量，不应重复追加到消息历史。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Data
public final class TextDelta implements ModelEvent {

    /**
     * 本次增量文本，可为空字符串。
     */
    private final String text;

    /**
     * 校验增量文本引用不为 null。
     *
     * @param text 本次增量文本
     * @throws NullPointerException 当增量文本为 null 时
     */
    public TextDelta(String text) {
        this.text = Objects.requireNonNull(text, "增量文本不能为 null");
    }
}
