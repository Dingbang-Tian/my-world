package com.dingbang.myworld.aiframework.embedding;

import lombok.Value;
import java.net.URI;
import java.util.Objects;

/**
 * 单个向量输入片段。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Value
public class EmbeddingPart {

    /**
     * 输入片段类型。
     */
    EmbeddingInputType type;

    /**
     * 文本、HTTPS URL 或 data URI。
     */
    String value;

    /**
     * 校验片段的类型与内容。
     *
     * @param type 片段类型
     * @param value 文本、HTTPS URL 或 data URI
     */
    public EmbeddingPart(EmbeddingInputType type, String value) {
        Objects.requireNonNull(type, "类型不能为空");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("向量输入不能为空");
        if (type != EmbeddingInputType.TEXT && !EmbeddingInput.validMedia(value, type)) {
            throw new IllegalArgumentException("媒体输入必须是匹配类型的 HTTPS URL 或 data URI");
        }
        this.type = type;
        this.value = value;
    }
}
