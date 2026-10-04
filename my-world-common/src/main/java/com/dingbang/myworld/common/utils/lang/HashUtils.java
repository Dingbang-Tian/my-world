package com.dingbang.myworld.common.utils.lang;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 提供跨模块复用的稳定哈希计算。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@UtilityClass
public class HashUtils {

    /**
     * SHA-256 算法名称。
     */
    private static final String SHA_256 = "SHA-256";

    /**
     * 计算 UTF-8 文本的 SHA-256 摘要。
     *
     * @param value 待计算文本
     * @return 小写十六进制摘要
     */
    public static String sha256(String value) {
        Objects.requireNonNull(value, "待哈希文本不能为空");
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节内容的 SHA-256 摘要。
     *
     * @param value 待计算字节
     * @return 小写十六进制摘要
     */
    public static String sha256(byte[] value) {
        Objects.requireNonNull(value, "待哈希内容不能为空");
        try {
            // JDK 21 必须提供 SHA-256；异常表示运行环境不满足项目基线。
            // 文本或文件内容的原始摘要字节。
            byte[] digest = MessageDigest.getInstance(SHA_256).digest(value);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }
}
