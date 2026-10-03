package com.dingbang.myworld.aiframework.embedding;

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
public final class EmbeddingInput {
    /**
     * 向量输入片段类型。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public enum Type { TEXT, IMAGE, VIDEO }

    /**
     * 单个向量输入片段。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public record Part(Type type, String value) {
        /**
         * 校验片段的类型与内容。
         *
         * @param type 片段类型
         * @param value 文本、HTTPS URL 或 data URI
         */
        public Part {
            Objects.requireNonNull(type, "类型不能为空");
            if (value == null || value.isBlank()) throw new IllegalArgumentException("向量输入不能为空");
            if (type != Type.TEXT && !validMedia(value, type)) {
                throw new IllegalArgumentException("媒体输入必须是匹配类型的 HTTPS URL 或 data URI");
            }
        }
    }

    /** 最大内存媒体字节数。 */
    public static final int MAX_MEDIA_BYTES = 10 * 1024 * 1024;
    /** 有序且不可变的内容片段。 */
    private final List<Part> parts;

    /**
     * 创建非空输入单元。
     *
     * @param parts 按语义顺序排列的片段
     */
    public EmbeddingInput(List<Part> parts) {
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
    public static EmbeddingInput text(String text) { return new EmbeddingInput(List.of(new Part(Type.TEXT, text))); }

    /**
     * 将有界内存媒体编码为 data URI。
     *
     * @param type 图片或视频类型
     * @param mimeType 与类型匹配的 MIME
     * @param bytes 媒体内容
     * @return 可加入输入的媒体片段
     */
    public static Part media(Type type, String mimeType, byte[] bytes) {
        if (type == Type.TEXT || mimeType == null || !mimeType.startsWith(type.name().toLowerCase() + "/")
                || bytes == null || bytes.length == 0 || bytes.length > MAX_MEDIA_BYTES) {
            throw new IllegalArgumentException("媒体类型或大小无效");
        }
        return new Part(type, "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes));
    }

    /**
     * 校验媒体地址。
     *
     * @param value 地址或 data URI
     * @param type 期望媒体类型
     * @return 地址是否可安全传递给供应商
     */
    private static boolean validMedia(String value, Type type) {
        if (value.startsWith("data:" + type.name().toLowerCase() + "/")) {
            return value.matches("data:[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/]+={0,2}")
                    && value.length() <= MAX_MEDIA_BYTES * 4L / 3L + 256;
        }
        try {
            /** 已解析的远程地址。 */
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** @return 不可变的有序内容片段 */
    public List<Part> getParts() { return parts; }
    /** @return 输入是否含图片或视频 */
    public boolean hasMedia() { return parts.stream().anyMatch(part -> part.type() != Type.TEXT); }
}
