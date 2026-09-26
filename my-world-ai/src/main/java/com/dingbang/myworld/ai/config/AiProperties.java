package com.dingbang.myworld.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * my-world AI 模块配置。
 *
 * @param enabled 是否启用 AI 模块
 * @param provider 模型供应商标识
 * @param defaultSystemPrompt 默认系统提示词
 * @param maxInputChars 用户输入最大字符数
 * @author Sebastian
 * @since 2026/09/25
 */
@ConfigurationProperties(prefix = "my-world.ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String defaultSystemPrompt,
        int maxInputChars) {
}
