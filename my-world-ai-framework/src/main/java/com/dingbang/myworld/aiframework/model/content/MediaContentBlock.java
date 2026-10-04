package com.dingbang.myworld.aiframework.model.content;

import lombok.Getter;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

/**
 * 以安全的远程地址或有界内存数据表示消息附件。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class MediaContentBlock implements ContentBlock {

    /**
     * 内存附件最大字节数。
     */
    public static final int MAX_BYTES = 1024 * 1024;
    /**
     * 附件类型。
     */
    @Getter
    private final MediaKind kind;
    /**
     * 媒体 MIME 类型。
     */
    @Getter
    private final String mimeType;
    /**
     * 附件名称。
     */
    @Getter
    private final String name;
    /**
     * HTTPS 来源，内存附件为空。
     */
    @Getter
    private final URI url;
    /**
     * 内存内容，远程附件为空。
     */
    private final byte[] bytes;

    /**
     * 固定附件内容并验证大小与来源。
     *
     * @param kind 媒体类型
     * @param mimeType MIME 类型
     * @param name 附件名称
     * @param url HTTPS 来源，与 bytes 二选一
     * @param bytes 内存内容，与 url 二选一
     */
    public MediaContentBlock(MediaKind kind, String mimeType, String name, URI url, byte[] bytes) {
        this.kind = Objects.requireNonNull(kind, "附件类型不能为空");
        if (mimeType == null || mimeType.isBlank() || !mimeType.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")) {
            throw new IllegalArgumentException("附件 MIME 类型无效");
        }
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new IllegalArgumentException("附件名称无效");
        }
        if (!mimeType.toLowerCase(java.util.Locale.ROOT).startsWith(kind.name().toLowerCase(java.util.Locale.ROOT) + "/")
                && !(kind == MediaKind.DOCUMENT && "application/pdf".equalsIgnoreCase(mimeType))) {
            throw new IllegalArgumentException("附件 MIME 与类型不匹配");
        }
        if ((url == null) == (bytes == null)) {
            throw new IllegalArgumentException("附件必须且只能指定一种来源");
        }
        if (url != null && (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null
                || url.getUserInfo() != null || url.getFragment() != null)) {
            throw new IllegalArgumentException("附件来源必须是无凭据的 HTTPS 地址");
        }
        if (bytes != null && (bytes.length == 0 || bytes.length > MAX_BYTES)) {
            throw new IllegalArgumentException("内存附件大小超出限制");
        }
        this.mimeType = mimeType;
        this.name = name;
        this.url = url;
        this.bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * @return 内存内容副本或 null
     */
    public byte[] getBytes() { return bytes == null ? null : Arrays.copyOf(bytes, bytes.length); }
}
