package com.dingbang.myworld.ai.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 在 Spring Boot 启动早期自动解密 ENC 格式的配置值。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class EncryptedPropertyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 存放解密配置覆盖值的属性源名称。
     */
    private static final String DECRYPTED_PROPERTY_SOURCE_NAME = "myWorldDecryptedProperties";

    /**
     * 延迟获取外部主密钥的函数。
     */
    private final Supplier<String> masterKeySupplier;

    /**
     * 创建优先从 JVM 系统属性读取主密钥的配置解密处理器。
     */
    public EncryptedPropertyEnvironmentPostProcessor() {
        this(ConfigSecretKeyResolver::resolveMasterKey);
    }

    /**
     * 创建使用指定主密钥来源的配置解密处理器。
     *
     * @param masterKeySupplier 主密钥来源
     */
    EncryptedPropertyEnvironmentPostProcessor(Supplier<String> masterKeySupplier) {
        this.masterKeySupplier = Objects.requireNonNull(masterKeySupplier, "主密钥来源不能为空");
    }

    /**
     * 扫描当前环境中的配置值并将解密结果作为最高优先级属性源加入环境。
     *
     * @param environment Spring 可配置环境
     * @param application Spring Boot 应用
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 所有可枚举属性源中的配置名称集合。
        Set<String> propertyNames = collectPropertyNames(environment);
        // 解密后需要覆盖原密文的配置映射。
        Map<String, Object> decryptedProperties = new LinkedHashMap<>();
        // 延迟读取的 Base64 编码主密钥。
        String masterKey = null;
        for (String propertyName : propertyNames) {
            // 按 Spring 属性源优先级解析后的当前配置值。
            String propertyValue = environment.getProperty(propertyName);
            if (!ConfigSecretCipher.isEncrypted(propertyValue)) {
                continue;
            }
            if (masterKey == null) {
                masterKey = masterKeySupplier.get();
            }
            if (masterKey == null || masterKey.isBlank()) {
                throw new IllegalStateException(
                        "配置项 " + propertyName + " 已加密，但未设置 JVM 参数 -D"
                                + ConfigSecretKeyResolver.MASTER_KEY_SYSTEM_PROPERTY + " 或环境变量 "
                                + ConfigSecretKeyResolver.MASTER_KEY_ENVIRONMENT_VARIABLE);
            }
            try {
                decryptedProperties.put(propertyName,
                        ConfigSecretCipher.decrypt(propertyValue, masterKey));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "配置项 " + propertyName + " 解密失败", exception);
            }
        }
        if (!decryptedProperties.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource(DECRYPTED_PROPERTY_SOURCE_NAME, decryptedProperties));
        }
    }

    /**
     * 在 Spring Boot 配置文件加载完成后执行自动解密。
     *
     * @return 处理器执行顺序
     */
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    /**
     * 收集环境中所有可枚举的配置名称。
     *
     * @param environment Spring 可配置环境
     * @return 配置名称集合
     */
    private static Set<String> collectPropertyNames(ConfigurableEnvironment environment) {
        // 保持属性源原始遍历顺序的配置名称集合。
        Set<String> propertyNames = new LinkedHashSet<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                    propertyNames.add(propertyName);
                }
            }
        }
        return propertyNames;
    }
}
