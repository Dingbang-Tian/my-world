package com.dingbang.myworld.agent.prompt;

import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.content.contentImpl.TextContentBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证模板来源优先级、渲染边界及快照行为。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
class PromptTemplateRegistryTest {

    /**
     * 使用默认资源创建真正的 SYSTEM 消息。
     */
    @Test
    void loadsDefaultsAndBuildsSystemMessage() {
        // 只装载公共默认资源。
        PromptRepository repository = registry(Collections.emptyMap(), Collections.emptyMap());
        PromptTemplateSnapshot snapshot = repository.get("agent/system");
        Map<String, String> variables = new HashMap<>();
        variables.put("agentName", "测试助手");
        variables.put("agentDescription", "负责回答问题");
        variables.put("skillInstructions", "无");
        variables.put("runtimeContext", "本地测试");

        assertThat(repository.keys()).containsExactly("agent/system", "agent/plan-step", "agent/summary", "agent/sub-agent");
        assertThat(snapshot.getRequiredVariables()).containsExactly("agentName", "agentDescription", "skillInstructions", "runtimeContext");
        assertThat(snapshot.getContentHash()).hasSize(64);
        assertThat(snapshot.toSystemMessage("system-1", variables).getRole()).isEqualTo(Role.SYSTEM);
        assertThat(((TextContentBlock) snapshot.toSystemMessage("system-1", variables)
                .getContentBlocks().get(0)).getText()).contains("你是 测试助手");
    }

    /**
     * 验证项目覆盖高于应用覆盖，未覆盖项使用默认资源。
     */
    @Test
    void appliesProjectThenApplicationThenDefault() {
        // 应用和项目分别覆盖同一模板。
        Map<String, PromptTemplateSpec> application = Collections.singletonMap("agent/system", inline("应用 {{name}}"));
        Map<String, PromptTemplateSpec> project = Collections.singletonMap("agent/system", inline("项目 {{name}}"));

        assertThat(registry(application, project).get("agent/system")
                .render(Collections.singletonMap("name", "甲"))).isEqualTo("项目 甲");
        assertThat(registry(application, Collections.emptyMap()).get("agent/system")
                .render(Collections.singletonMap("name", "甲"))).isEqualTo("应用 甲");
        assertThat(registry(Collections.emptyMap(), Collections.emptyMap()).get("agent/system")
                .getContent()).startsWith("你是 ");
    }

    /**
     * 验证缺少变量会报错，插入的用户文本不会再次被解释为表达式。
     */
    @Test
    void validatesVariablesWithoutEvaluatingUserTextAgain() {
        // 用户输入只作为变量值进行一次替换。
        PromptTemplateSnapshot snapshot = registry(Collections.emptyMap(),
                Collections.singletonMap("agent/system", inline("任务：{{userTask}}"))).get("agent/system");

        assertThatThrownBy(() -> snapshot.render(Collections.emptyMap()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userTask");
        assertThat(snapshot.render(Collections.singletonMap("userTask", "请保留 {{secret}} 原样")))
                .isEqualTo("任务：请保留 {{secret}} 原样");
    }

    /**
     * 验证文件变化只影响新建仓库，不改动已取得的快照。
     *
     * @param directory 临时模板目录
     * @throws IOException 临时文件写入失败时
     */
    @Test
    void keepsSnapshotStableUntilRegistryIsRecreated(@TempDir Path directory) throws IOException {
        // 模拟项目模板修改与应用重启。
        Path file = directory.resolve("system.md");
        Files.write(file, "第一版 {{name}}".getBytes(StandardCharsets.UTF_8));
        PromptTemplateSpec spec = new PromptTemplateSpec();
        spec.setLocation(file.toUri().toString());
        Map<String, PromptTemplateSpec> project = Collections.singletonMap("agent/system", spec);
        PromptTemplateSnapshot first = registry(Collections.emptyMap(), project).get("agent/system");

        Files.write(file, "第二版 {{name}}".getBytes(StandardCharsets.UTF_8));
        PromptTemplateSnapshot second = registry(Collections.emptyMap(), project).get("agent/system");
        assertThat(first.render(Collections.singletonMap("name", "甲"))).isEqualTo("第一版 甲");
        assertThat(second.render(Collections.singletonMap("name", "甲"))).isEqualTo("第二版 甲");
        assertThat(second.getContentHash()).isNotEqualTo(first.getContentHash());
    }

    /**
     * 验证无效位置、空模板、未知标识及互斥配置在加载时被拒绝。
     */
    @Test
    void rejectsInvalidConfigurationDuringLoading() {
        // 各异常在仓库构造阶段出现，而不是等到模型请求时才出现。
        assertThatThrownBy(() -> registry(Collections.emptyMap(),
                Collections.singletonMap("agent/unknown", inline("内容"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("未知模板");
        assertThatThrownBy(() -> registry(Collections.emptyMap(),
                Collections.singletonMap("agent/system", inline("  "))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inline 不能为空");
        assertThatThrownBy(() -> registry(Collections.emptyMap(),
                Collections.singletonMap("agent/system", location("https://example.com/prompt.md"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("模板位置必须");
        assertThatThrownBy(() -> registry(Collections.emptyMap(),
                Collections.singletonMap("agent/system", location("file:/missing-s03-template.md"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("无法读取模板");
        PromptTemplateSpec both = inline("内容");
        both.setLocation("classpath:/prompts/agent/system.md");
        assertThatThrownBy(() -> registry(Collections.emptyMap(), Collections.singletonMap("agent/system", both)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能同时配置");
        assertThatThrownBy(() -> registry(Collections.emptyMap(),
                Collections.singletonMap("agent/system", inline("{{bad.expression}}"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("非法占位符");
    }

    /**
     * 验证显式禁用模板不会暴露给调用方。
     */
    @Test
    void disablesTemplateOnlyWhenExplicitlyConfigured() {
        // null 表示继承，false 才关闭模板。
        PromptTemplateSpec disabled = new PromptTemplateSpec();
        disabled.setEnabled(false);
        PromptRepository repository = registry(Collections.emptyMap(),
                Collections.singletonMap("agent/sub-agent", disabled));

        assertThat(repository.keys()).doesNotContain("agent/sub-agent");
        assertThatThrownBy(() -> repository.get("agent/sub-agent"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已禁用");
    }

    /**
     * 创建仓库以执行独立的模板行为验证。
     *
     * @param application 应用覆盖
     * @param project 项目覆盖
     * @return 已加载仓库
     */
    private static PromptRepository registry(Map<String, PromptTemplateSpec> application,
                                             Map<String, PromptTemplateSpec> project) {
        return new PromptTemplateRegistry(new DefaultResourceLoader(), application, project);
    }

    /**
     * 创建内联模板配置。
     *
     * @param content 模板内容
     * @return 配置对象
     */
    private static PromptTemplateSpec inline(String content) {
        PromptTemplateSpec spec = new PromptTemplateSpec();
        spec.setInline(content);
        return spec;
    }

    /**
     * 创建文件位置模板配置。
     *
     * @param value 模板位置
     * @return 配置对象
     */
    private static PromptTemplateSpec location(String value) {
        PromptTemplateSpec spec = new PromptTemplateSpec();
        spec.setLocation(value);
        return spec;
    }
}
