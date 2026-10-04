package com.dingbang.myworld.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * my-world AI 模块配置。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Data
@ConfigurationProperties(prefix = "my-world.ai")
public class AiProperties {

    /**
     * 是否启用 AI 模块。
     */
    private boolean enabled;

    /**
     * 模型供应商标识。
     */
    private String provider;

    /**
     * 普通对话使用的默认系统提示词。
     */
    private String defaultSystemPrompt;

    /**
     * 用户输入允许的最大字符数。
     */
    private int maxInputChars;
}
