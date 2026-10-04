package com.dingbang.myworld.common.crypto;

import java.io.Console;
import java.util.Arrays;

/**
 * 从终端安全读取配置明文并生成 ENC 密文。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public final class ConfigSecretEncryptCommand {

    /**
     * 禁止实例化配置密文生成命令类。
     */
    private ConfigSecretEncryptCommand() {
        throw new IllegalStateException("配置密文生成命令类不允许实例化");
    }

    /**
     * 读取主密钥和待加密配置，并向终端输出 ENC 密文。
     *
     * @param args 命令行参数，本命令不接受参数
     */
    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("本命令不接受命令行参数，避免明文进入终端历史");
        }
        // 支持隐藏输入内容的当前系统终端。
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("未检测到交互式终端，请在系统终端中执行此命令");
        }
        // 优先从 JVM 参数读取 Base64 编码的主密钥。
        String masterKey = ConfigSecretKeyResolver.resolveMasterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    "请先设置 JVM 参数 -D"
                            + ConfigSecretKeyResolver.MASTER_KEY_SYSTEM_PROPERTY
                            + " 或环境变量 "
                            + ConfigSecretKeyResolver.MASTER_KEY_ENVIRONMENT_VARIABLE);
        }
        // 从终端隐藏读取的待加密配置字符。
        char[] plainTextCharacters = console.readPassword("请输入待加密配置：");
        if (plainTextCharacters == null || plainTextCharacters.length == 0) {
            throw new IllegalArgumentException("待加密配置不能为空");
        }
        try {
            // 可直接写入 Spring 配置文件的 ENC 密文。
            String encryptedValue = ConfigSecretCipher.encrypt(
                    new String(plainTextCharacters), masterKey);
            console.format("%s%n", encryptedValue);
        } finally {
            Arrays.fill(plainTextCharacters, '\0');
        }
    }
}
