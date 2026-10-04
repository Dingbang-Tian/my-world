package com.dingbang.myworld.common.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置密文加解密和自动加载测试。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
class ConfigSecretCipherTest {

    /**
     * 测试使用的固定 32 字节主密钥。
     */
    private static final String MASTER_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    /**
     * 验证配置明文能够完成加密和解密往返。
     */
    @Test
    void shouldEncryptAndDecryptConfigurationValue() {
        // 模拟的配置明文。
        String plainText = "sk-example-secret";
        // AES-GCM 加密后的配置值。
        String encryptedValue = ConfigSecretCipher.encrypt(plainText, MASTER_KEY);

        assertThat(encryptedValue).startsWith("ENC(v1:");
        assertThat(ConfigSecretCipher.decrypt(encryptedValue, MASTER_KEY)).isEqualTo(plainText);
    }

    /**
     * 验证相同明文每次都会使用随机初始化向量生成不同密文。
     */
    @Test
    void shouldGenerateDifferentCipherTextForSamePlainText() {
        // 第一次生成的配置密文。
        String firstEncryptedValue = ConfigSecretCipher.encrypt("same-secret", MASTER_KEY);
        // 第二次生成的配置密文。
        String secondEncryptedValue = ConfigSecretCipher.encrypt("same-secret", MASTER_KEY);

        assertThat(firstEncryptedValue).isNotEqualTo(secondEncryptedValue);
    }

    /**
     * 验证使用错误主密钥时不会返回配置明文。
     */
    @Test
    void shouldRejectWrongMasterKey() {
        // 使用正确主密钥生成的配置密文。
        String encryptedValue = ConfigSecretCipher.encrypt("protected-secret", MASTER_KEY);
        // 与正确主密钥不同的 32 字节主密钥。
        String wrongMasterKey = Base64.getEncoder()
                .encodeToString("abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> ConfigSecretCipher.decrypt(encryptedValue, wrongMasterKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("配置解密失败");
    }

    /**
     * 验证密文被篡改后无法通过 GCM 完整性校验。
     */
    @Test
    void shouldRejectTamperedCipherText() {
        // 使用正确主密钥生成的原始密文。
        String encryptedValue = ConfigSecretCipher.encrypt("protected-secret", MASTER_KEY);
        // 修改最后一个 Base64 字符以模拟存储或传输过程中的篡改。
        char replacementCharacter = encryptedValue.charAt(encryptedValue.length() - 2) == 'A' ? 'B' : 'A';
        String tamperedValue = encryptedValue.substring(0, encryptedValue.length() - 2)
                + replacementCharacter
                + encryptedValue.substring(encryptedValue.length() - 1);

        assertThatThrownBy(() -> ConfigSecretCipher.decrypt(tamperedValue, MASTER_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("配置解密失败");
    }

    /**
     * 验证 Spring 环境处理器会自动使用明文覆盖 ENC 配置。
     */
    @Test
    void shouldDecryptEncryptedSpringProperty() {
        // 等待自动解密的配置密文。
        String encryptedValue = ConfigSecretCipher.encrypt("spring-secret", MASTER_KEY);
        // 包含测试配置属性源的 Spring 环境。
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "encryptedTestProperties", Map.of("test.secret", encryptedValue)));
        // 使用测试主密钥的自动解密处理器。
        EncryptedPropertyEnvironmentPostProcessor processor =
                new EncryptedPropertyEnvironmentPostProcessor(() -> MASTER_KEY);

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("test.secret")).isEqualTo("spring-secret");
    }

    /**
     * 验证环境变量密文能够通过配置占位符自动传递到目标配置。
     */
    @Test
    void shouldDecryptEncryptedPlaceholderProperty() {
        // 模拟通过环境变量注入的 DeepSeek 密文。
        String encryptedValue = ConfigSecretCipher.encrypt("deepseek-secret", MASTER_KEY);
        // 按插入顺序保存环境变量及其 Spring 配置占位符。
        Map<String, Object> sourceProperties = new LinkedHashMap<>();
        sourceProperties.put("DEEPSEEK_API_KEY", encryptedValue);
        sourceProperties.put("spring.ai.openai.api-key", "${DEEPSEEK_API_KEY:}");
        // 包含真实配置结构的 Spring 测试环境。
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("placeholderTestProperties", sourceProperties));
        // 使用测试主密钥的自动解密处理器。
        EncryptedPropertyEnvironmentPostProcessor processor =
                new EncryptedPropertyEnvironmentPostProcessor(() -> MASTER_KEY);

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("DEEPSEEK_API_KEY")).isEqualTo("deepseek-secret");
        assertThat(environment.getProperty("spring.ai.openai.api-key")).isEqualTo("deepseek-secret");
    }

    /**
     * 验证存在加密配置但缺少主密钥时阻止应用继续启动。
     */
    @Test
    void shouldRejectEncryptedPropertyWithoutMasterKey() {
        // 等待自动解密的配置密文。
        String encryptedValue = ConfigSecretCipher.encrypt("spring-secret", MASTER_KEY);
        // 包含加密配置的 Spring 测试环境。
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "missingKeyTestProperties", Map.of("test.secret", encryptedValue)));
        // 模拟运行环境没有设置主密钥。
        EncryptedPropertyEnvironmentPostProcessor processor =
                new EncryptedPropertyEnvironmentPostProcessor(() -> null);

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("my-world.config.master-key");
    }

    /**
     * 验证 JVM 系统属性中的主密钥能够被优先读取。
     */
    @Test
    void shouldResolveMasterKeyFromSystemProperty() {
        // 保存测试执行前可能存在的 JVM 系统属性。
        String originalMasterKey = System.getProperty(
                ConfigSecretKeyResolver.MASTER_KEY_SYSTEM_PROPERTY);
        try {
            System.setProperty(
                    ConfigSecretKeyResolver.MASTER_KEY_SYSTEM_PROPERTY,
                    MASTER_KEY);

            assertThat(ConfigSecretKeyResolver.resolveMasterKey())
                    .isEqualTo(MASTER_KEY);
        } finally {
            if (originalMasterKey == null) {
                System.clearProperty(
                        ConfigSecretKeyResolver.MASTER_KEY_SYSTEM_PROPERTY);
            } else {
                System.setProperty(
                        ConfigSecretKeyResolver.MASTER_KEY_SYSTEM_PROPERTY,
                        originalMasterKey);
            }
        }
    }

    /**
     * 验证 JVM 系统属性中的普通口令会自动派生为 AES-256 主密钥。
     */
    @Test
    void shouldDeriveMasterKeyFromSystemPropertyPassphrase() {
        // 保存测试执行前可能存在的 JVM 系统属性。
        String originalMasterKey = System.getProperty(
                ConfigSecretKeyResolver.MASTER_KEY_SYSTEM_PROPERTY);
        try {
            System.setProperty(
                    ConfigSecretKeyResolver.MASTER_KEY_SYSTEM_PROPERTY,
                    "test-passphrase");

            assertThat(ConfigSecretKeyResolver.resolveMasterKey())
                    .isEqualTo("dXTwG569OyXjZA+IQnJg9gWHS6dv7+2AJCHYup4jjJM=");
        } finally {
            if (originalMasterKey == null) {
                System.clearProperty(
                        ConfigSecretKeyResolver.MASTER_KEY_SYSTEM_PROPERTY);
            } else {
                System.setProperty(
                        ConfigSecretKeyResolver.MASTER_KEY_SYSTEM_PROPERTY,
                        originalMasterKey);
            }
        }
    }
}
