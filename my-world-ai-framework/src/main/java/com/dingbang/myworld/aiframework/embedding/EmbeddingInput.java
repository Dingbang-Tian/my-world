package com.dingbang.myworld.aiframework.embedding;

import lombok.Getter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一个可包含文本、图片和视频的向量输入语义单元。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Getter
public final class EmbeddingInput {


    /**
     * 最大内存媒体字节数。
     */
    public static final int MAX_MEDIA_BYTES = 10 * 1024 * 1024;
    /**
     * 有序且不可变的内容片段。
     */
    private final List<EmbeddingPart> parts;

    /**
     * 创建非空输入单元。
     *
     * @param parts 按语义顺序排列的片段
     */
    public EmbeddingInput(List<EmbeddingPart> parts) {
        if (parts == null || parts.isEmpty()) throw new IllegalArgumentException("向量输入不能空");
        parts.forEach(Objects::requireNonNull);
        this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
    }

    /**
     * 创建纯文本输入。
     *
     * @param text 非空文本
     * @return 文本输入
     */
    public static EmbeddingInput text(String text) { return new EmbeddingInput(List.of(new EmbeddingPart(EmbeddingInputType.TEXT, text))); }

    /**
     * 将有界内存媒体编码为 data URI。
     *
     * @param type 图片或视频类型
     * @param mimeType 与类型匹配的 MIME
     * @param bytes 媒体内容
     * @return 可加入输入的媒体片段
     */
    public static EmbeddingPart media(EmbeddingInputType type, String mimeType, byte[] bytes) {
        if (type == EmbeddingInputType.TEXT || mimeType == null || !mimeType.startsWith(type.name().toLowerCase() + "/")
                || bytes == null || bytes.length == 0 || bytes.length > MAX_MEDIA_BYTES) {
            throw new IllegalArgumentException("媒体类型或大小无效");
        }
        return new EmbeddingPart(type, "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes));
    }

    /**
     * 校验媒体地址。
     *
     * @param value 地址或 data URI
     * @param type 期望媒体类型
     * @return 地址是否可安全传递给供应商
     */
    static boolean validMedia(String value, EmbeddingInputType type) {
        if (value.startsWith("data:" + type.name().toLowerCase() + "/")) {
            return value.matches("data:[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/]+={0,2}")
                    && value.length() <= MAX_MEDIA_BYTES * 4L / 3L + 256;
        }
        try {
            // 远程媒体只接受不携带凭据和锚点的 HTTPS 绝对地址。
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * @return 输入是否含图片或视频
     */
    public boolean hasMedia() {
        return parts.stream().anyMatch(part -> part.getType() != EmbeddingInputType.TEXT);
    }
}
