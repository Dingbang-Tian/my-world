package com.dingbang.myworld.agent.prompt;

import java.util.Map;

/**
 * 应用模块向公共 Agent 提供模板覆盖的扩展接口。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public interface PromptTemplateContributor {

    /**
     * 返回以完整模板标识为键的应用覆盖配置。
     *
     * @return 模板覆盖映射
     */
    Map<String, PromptTemplateSpec> getTemplates();
}
