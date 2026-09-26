package com.dingbang.myworld.ai.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 使用 AES-GCM 加解密配置密文。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public final class ConfigSecretCipher {

    /**
     * 配置密文前缀。
     */
    private static final String ENCRYPTED_PREFIX = "ENC(v1:";

    /**
     * 配置密文后缀。
     */
    private static final String ENCRYPTED_SUFFIX = ")";

    /**
     * AES-GCM 加密算法名称。
     */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * AES-256 密钥字节数。
     */
    private static final int KEY_LENGTH_BYTES = 32;

    /**
     * GCM 推荐初始化向量字节数。
     */
    private static final int INITIALIZATION_VECTOR_LENGTH_BYTES = 12;

    /**
     * GCM 认证标签位数。
     */
    private static final int AUTHENTICATION_TAG_LENGTH_BITS = 128;

    /**
     * GCM 认证标签字节数。
     */
    private static final int AUTHENTICATION_TAG_LENGTH_BYTES =
            AUTHENTICATION_TAG_LENGTH_BITS / Byte.SIZE;

    /**
     * 密文最小字节数，包含初始化向量和认证标签。
     */
    private static final int MINIMUM_PAYLOAD_LENGTH_BYTES =
            INITIALIZATION_VECTOR_LENGTH_BYTES + AUTHENTICATION_TAG_LENGTH_BYTES;

    /**
     * 用于隔离配置密文用途的附加认证数据。
     */
    private static final byte[] ADDITIONAL_AUTHENTICATED_DATA =
            "my-world-config:v1".getBytes(StandardCharsets.UTF_8);

    /**
     * 生成不可预测初始化向量的安全随机数生成器。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 禁止实例化配置密文工具类。
     */
    private ConfigSecretCipher() {
        throw new IllegalStateException("配置密文工具类不允许实例化");
    }

    /**
     * 使用 Base64 编码的 AES-256 主密钥加密配置明文。
     *
     * @param plainText 配置明文
     * @param base64MasterKey Base64 编码的 32 字节主密钥
     * @return 可直接写入配置文件的 ENC 密文
     */
    public static String encrypt(String plainText, String base64MasterKey) {
        if (plainText == null) {
            throw new IllegalArgumentException("待加密配置不能为空");
        }
        // 本次加密使用的随机初始化向量。
        byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(initializationVector);
        try {
            // 由外部主密钥构造的 AES 密钥。
            SecretKeySpec secretKey = createSecretKey(base64MasterKey);
            // 执行 AES-GCM 加密的密码器。
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey,
                    new GCMParameterSpec(AUTHENTICATION_TAG_LENGTH_BITS, initializationVector));
            cipher.updateAAD(ADDITIONAL_AUTHENTICATED_DATA);
            // 包含密文和 GCM 认证标签的加密结果。
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            // 合并初始化向量和加密结果的载荷缓冲区。
            ByteBuffer payloadBuffer = ByteBuffer.allocate(initializationVector.length + encryptedBytes.length);
            payloadBuffer.put(initializationVector);
            payloadBuffer.put(encryptedBytes);
            // 便于写入 YAML 的 URL 安全 Base64 载荷。
            String encodedPayload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(payloadBuffer.array());
            return ENCRYPTED_PREFIX + encodedPayload + ENCRYPTED_SUFFIX;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("配置加密失败", exception);
        }
    }

    /**
     * 使用 Base64 编码的 AES-256 主密钥解密 ENC 配置密文。
     *
     * @param encryptedValue ENC 格式的配置密文
     * @param base64MasterKey Base64 编码的 32 字节主密钥
     * @return 解密后的配置明文
     */
    public static String decrypt(String encryptedValue, String base64MasterKey) {
        if (!isEncrypted(encryptedValue)) {
            throw new IllegalArgumentException("配置值不是受支持的 ENC(v1:...) 格式");
        }
        // 避免重复清理密文首尾空白。
        String normalizedEncryptedValue = encryptedValue.trim();
        // 去除格式标记后的 Base64 密文载荷。
        String encodedPayload = normalizedEncryptedValue.substring(
                ENCRYPTED_PREFIX.length(),
                normalizedEncryptedValue.length() - ENCRYPTED_SUFFIX.length());
        if (encodedPayload.isEmpty()) {
            throw new IllegalArgumentException("配置密文载荷不能为空");
        }
        try {
            // 解码后的初始化向量、密文和认证标签载荷。
            byte[] payload = Base64.getUrlDecoder().decode(encodedPayload);
            if (payload.length < MINIMUM_PAYLOAD_LENGTH_BYTES) {
                throw new IllegalArgumentException("配置密文载荷长度无效");
            }
            // 从载荷中读取的初始化向量。
            byte[] initializationVector = Arrays.copyOfRange(
                    payload, 0, INITIALIZATION_VECTOR_LENGTH_BYTES);
            // 从载荷中读取的密文和认证标签。
            byte[] encryptedBytes = Arrays.copyOfRange(
                    payload, INITIALIZATION_VECTOR_LENGTH_BYTES, payload.length);
            // 由外部主密钥构造的 AES 密钥。
            SecretKeySpec secretKey = createSecretKey(base64MasterKey);
            // 执行 AES-GCM 解密和完整性校验的密码器。
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,
                    new GCMParameterSpec(AUTHENTICATION_TAG_LENGTH_BITS, initializationVector));
            cipher.updateAAD(ADDITIONAL_AUTHENTICATED_DATA);
            // 通过认证后得到的配置明文字节。
            byte[] plainTextBytes = cipher.doFinal(encryptedBytes);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("配置解密失败，请检查主密钥和密文是否正确", exception);
        }
    }

    /**
     * 判断配置值是否为受支持的 ENC 密文格式。
     *
     * @param value 待判断的配置值
     * @return 是受支持的密文格式时返回 true
     */
    public static boolean isEncrypted(String value) {
        if (value == null) {
            return false;
        }
        // 去除配置文件可能引入的首尾空白后的值。
        String trimmedValue = value.trim();
        return trimmedValue.startsWith(ENCRYPTED_PREFIX)
                && trimmedValue.endsWith(ENCRYPTED_SUFFIX);
    }

    /**
     * 解析并校验 Base64 编码的 AES-256 主密钥。
     *
     * @param base64MasterKey Base64 编码的主密钥
     * @return AES 密钥对象
     */
    private static SecretKeySpec createSecretKey(String base64MasterKey) {
        if (base64MasterKey == null || base64MasterKey.isBlank()) {
            throw new IllegalArgumentException("配置主密钥不能为空");
        }
        // Base64 解码后的主密钥字节。
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64MasterKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("配置主密钥必须是有效的 Base64 字符串", exception);
        }
        try {
            if (keyBytes.length != KEY_LENGTH_BYTES) {
                throw new IllegalArgumentException("配置主密钥解码后必须为 32 字节");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }
}
