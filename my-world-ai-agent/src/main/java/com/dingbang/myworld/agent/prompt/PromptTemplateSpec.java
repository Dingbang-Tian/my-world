package com.dingbang.myworld.agent.prompt;

import lombok.Data;

/**
 * 单个提示词模板的可绑定配置，inline 与 location 只能选择一个。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class PromptTemplateSpec {

    /**
     * 直接写入配置的模板文本。
     */
    private String inline;

    /**
     * classpath 或本地文件模板位置。
     */
    private String location;

    /**
     * 显式关闭模板；null 表示继承上层配置。
     */
    private Boolean enabled;
}
