package com.dingbang.myworld.aiframework.model.content.contentImpl;

import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

/**
 * 文本消息内容块。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Data
public final class TextContentBlock implements ContentBlock {

    /**
     * 文本内容，可为空字符串。
     */
    private final String text;

    /**
     * 校验文本引用不为 null。
     *
     * @param text 文本内容
     * @throws NullPointerException 当文本引用为 null 时
     */
    public TextContentBlock(String text) {
        if (StringUtils.isBlank(text)) {
            throw new IllegalArgumentException("文本内容不能为空");
        }
        this.text = text;
    }
}
