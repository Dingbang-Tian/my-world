package com.dingbang.myworld.ai.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/**
 * 配置加解密主密钥解析器。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public final class ConfigSecretKeyResolver {

    /**
     * AES-256 主密钥字节数。
     */
    private static final int KEY_LENGTH_BYTES = 32;

    /**
     * 自定义口令派生算法名称。
     */
    private static final String KEY_DERIVATION_ALGORITHM = "SHA-256";

    /**
     * 外部主密钥 JVM 系统属性名称。
     */
    public static final String MASTER_KEY_SYSTEM_PROPERTY = "my-world.config.master-key";

    /**
     * 外部主密钥环境变量名称。
     */
    public static final String MASTER_KEY_ENVIRONMENT_VARIABLE = "MY_WORLD_CONFIG_MASTER_KEY";

    /**
     * 禁止实例化配置加解密主密钥解析器。
     */
    private ConfigSecretKeyResolver() {
        throw new IllegalStateException("配置加解密主密钥解析器不允许实例化");
    }

    /**
     * 优先读取 JVM 系统属性中的主密钥或口令，不存在时兼容环境变量。
     *
     * @return Base64 编码的主密钥，未配置时返回 null
     */
    public static String resolveMasterKey() {
        // JVM 参数便于在 IDEA 和启动脚本中按应用配置。
        String configuredMasterKey = System.getProperty(MASTER_KEY_SYSTEM_PROPERTY);
        if (configuredMasterKey == null || configuredMasterKey.isBlank()) {
            configuredMasterKey = System.getenv(MASTER_KEY_ENVIRONMENT_VARIABLE);
        }
        return normalizeMasterKey(configuredMasterKey);
    }

    /**
     * 保留合法的 Base64 AES-256 密钥，并将普通口令派生为兼容格式。
     *
     * @param configuredValue JVM 参数或环境变量中的配置值
     * @return Base64 编码的 AES-256 主密钥，未配置时返回 null
     */
    static String normalizeMasterKey(String configuredValue) {
        if (configuredValue == null || configuredValue.isBlank()) {
            return null;
        }
        // 去除启动参数复制时可能带入的首尾空白。
        String normalizedValue = configuredValue.trim();
        if (isBase64Aes256Key(normalizedValue)) {
            return normalizedValue;
        }
        try {
            // 普通口令使用 SHA-256 派生为固定 32 字节主密钥。
            MessageDigest messageDigest = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM);
            byte[] derivedKey = messageDigest.digest(
                    normalizedValue.getBytes(StandardCharsets.UTF_8));
            try {
                return Base64.getEncoder().encodeToString(derivedKey);
            } finally {
                Arrays.fill(derivedKey, (byte) 0);
            }
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256 主密钥派生", exception);
        }
    }

    /**
     * 判断配置值是否为 Base64 编码的 AES-256 主密钥。
     *
     * @param value 待判断的配置值
     * @return 解码后为 32 字节时返回 true
     */
    private static boolean isBase64Aes256Key(String value) {
        // Base64 解码后的候选主密钥字节。
        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        try {
            return decodedKey.length == KEY_LENGTH_BYTES;
        } finally {
            Arrays.fill(decodedKey, (byte) 0);
        }
    }
}
