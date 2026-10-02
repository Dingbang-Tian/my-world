package com.dingbang.myworld.agent.prompt;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 在启动或注册时解析默认、应用和项目模板并固定快照。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class PromptTemplateRegistry implements PromptRepository {

    /**
     * 公共 Agent 模板和明确的默认资源位置。
     */
    private static final Map<String, String> DEFAULT_LOCATIONS;

    static {
        // 明确登记资源，避免依赖同名 classpath 文件的偶然覆盖顺序。
        Map<String, String> locations = new LinkedHashMap<>();
        locations.put("agent/system", "classpath:/prompts/agent/system.md");
        locations.put("agent/plan-step", "classpath:/prompts/agent/plan-step.md");
        locations.put("agent/summary", "classpath:/prompts/agent/summary.md");
        locations.put("agent/sub-agent", "classpath:/prompts/agent/sub-agent.md");
        DEFAULT_LOCATIONS = Collections.unmodifiableMap(locations);
    }

    /**
     * 启用的不可变模板快照集合。
     */
    private final Map<String, PromptTemplateSnapshot> snapshots;

    /**
     * 加载并合并模板，优先级为项目、应用、公共默认值。
     *
     * @param resourceLoader Spring 资源加载器
     * @param applicationOverrides 应用提供的完整模板标识覆盖
     * @param projectOverrides 项目配置提供的完整模板标识覆盖
     * @throws IllegalArgumentException 配置、模板位置或内容非法时
     */
    public PromptTemplateRegistry(ResourceLoader resourceLoader,
                                  Map<String, PromptTemplateSpec> applicationOverrides,
                                  Map<String, PromptTemplateSpec> projectOverrides) {
        Objects.requireNonNull(resourceLoader, "资源加载器不能为 null");
        validateOverrides(applicationOverrides, "应用");
        validateOverrides(projectOverrides, "项目");

        Map<String, PromptTemplateSnapshot> loaded = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : DEFAULT_LOCATIONS.entrySet()) {
            String templateId = entry.getKey();
            String content = read(resourceLoader, entry.getValue());
            Boolean enabled = Boolean.TRUE;
            PromptTemplateSpec application = applicationOverrides.get(templateId);
            PromptTemplateSpec project = projectOverrides.get(templateId);
            if (application != null) {
                content = overrideContent(resourceLoader, application, content);
                if (application.getEnabled() != null) {
                    enabled = application.getEnabled();
                }
            }
            if (project != null) {
                content = overrideContent(resourceLoader, project, content);
                if (project.getEnabled() != null) {
                    enabled = project.getEnabled();
                }
            }
            PromptTemplateSnapshot snapshot = new PromptTemplateSnapshot(templateId, content);
            if (enabled) {
                loaded.put(templateId, snapshot);
            }
        }
        this.snapshots = Collections.unmodifiableMap(loaded);
    }

    /**
     * 获取已启用的模板快照。
     *
     * @param templateId 模板标识
     * @return 模板快照
     * @throws IllegalArgumentException 模板未知或被禁用时
     */
    @Override
    public PromptTemplateSnapshot get(String templateId) {
        PromptTemplateSnapshot snapshot = snapshots.get(templateId);
        if (snapshot == null) {
            throw new IllegalArgumentException("未知或已禁用的模板: " + templateId);
        }
        return snapshot;
    }

    /**
     * 返回已启用的模板标识。
     *
     * @return 不可修改的标识集合
     */
    @Override
    public Set<String> getKeys() {
        return snapshots.keySet();
    }

    /**
     * 校验覆盖配置的模板标识及来源形式。
     *
     * @param overrides 覆盖配置
     * @param source 配置来源
     * @throws IllegalArgumentException 标识、来源或文本为空时
     */
    private static void validateOverrides(Map<String, PromptTemplateSpec> overrides, String source) {
        Objects.requireNonNull(overrides, source + "模板配置不能为 null");
        for (Map.Entry<String, PromptTemplateSpec> entry : overrides.entrySet()) {
            if (!DEFAULT_LOCATIONS.containsKey(entry.getKey())) {
                throw new IllegalArgumentException(source + "配置含未知模板: " + entry.getKey());
            }
            PromptTemplateSpec spec = Objects.requireNonNull(entry.getValue(), "模板配置不能为 null");
            if (spec.getInline() != null && spec.getLocation() != null) {
                throw new IllegalArgumentException("模板 " + entry.getKey() + " 不能同时配置 inline 和 location");
            }
            if (spec.getInline() != null && StringUtils.isBlank(spec.getInline())) {
                throw new IllegalArgumentException("模板 " + entry.getKey() + " 的 inline 不能为空");
            }
            if (spec.getLocation() != null && StringUtils.isBlank(spec.getLocation())) {
                throw new IllegalArgumentException("模板 " + entry.getKey() + " 的 location 不能为空");
            }
        }
    }

    /**
     * 根据覆盖配置选择文本；没有新来源时继承前一层。
     *
     * @param resourceLoader Spring 资源加载器
     * @param spec 当前层配置
     * @param inherited 前一层文本
     * @return 合并后的文本
     */
    private static String overrideContent(ResourceLoader resourceLoader, PromptTemplateSpec spec, String inherited) {
        if (spec.getInline() != null) {
            return spec.getInline();
        }
        if (spec.getLocation() != null) {
            return read(resourceLoader, spec.getLocation());
        }
        return inherited;
    }

    /**
     * 只读取明确声明的 classpath 或本地文件位置。
     *
     * @param resourceLoader Spring 资源加载器
     * @param location 模板位置
     * @return UTF-8 模板内容
     * @throws IllegalArgumentException 位置非法、文件无法读取或内容为空时
     */
    private static String read(ResourceLoader resourceLoader, String location) {
        if (StringUtils.isBlank(location)
                || !(location.startsWith("classpath:/") || location.startsWith("file:/"))) {
            throw new IllegalArgumentException("模板位置必须以 classpath:/ 或 file:/ 开始: " + location);
        }
        Resource resource = resourceLoader.getResource(location);
        try (InputStream input = resource.getInputStream()) {
            String content = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
            if (StringUtils.isBlank(content)) {
                throw new IllegalArgumentException("模板内容不能为空: " + location);
            }
            return content;
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取模板: " + location, exception);
        }
    }
}
