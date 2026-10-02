package com.dingbang.myworld.agent.config;

import com.dingbang.myworld.agent.prompt.PromptTemplateContributor;
import com.dingbang.myworld.agent.prompt.PromptTemplateSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为装配测试提供位于应用资源中的模板覆盖。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Component
public final class TestPromptTemplateContributor implements PromptTemplateContributor {

    /**
     * 返回系统和摘要模板的应用资源位置。
     *
     * @return 应用模板覆盖配置
     */
    @Override
    public Map<String, PromptTemplateSpec> getTemplates() {
        // 测试资源模拟应用模块自带的模板文件。
        Map<String, PromptTemplateSpec> templates = new LinkedHashMap<>();
        PromptTemplateSpec system = new PromptTemplateSpec();
        system.setLocation("classpath:/s03-app-system.md");
        templates.put("agent/system", system);
        PromptTemplateSpec summary = new PromptTemplateSpec();
        summary.setLocation("classpath:/s03-app-summary.md");
        templates.put("agent/summary", summary);
        return templates;
    }
}
