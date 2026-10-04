package com.dingbang.myworld.aiapp.codegen.api;

import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.aiapp.codegen.application.CodegenFactory;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * 供普通 JAR 消费者创建绑定可信工作目录的代码生成应用。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CodegenSdk {

    /**
     * 使用内置提示词创建只读代码生成服务。
     *
     * @param gateway 单次模型入口
     * @param modelId 可信模型标识
     * @param workspace 可信工作目录
     * @return 可运行并提供公共 AgentService 的代码生成服务
     * @throws IllegalArgumentException 工作目录不可访问或模型标识无效时
     */
    public static CodegenService readOnly(ModelGateway gateway, String modelId, Path workspace) {
        Objects.requireNonNull(gateway, "模型入口不能为空");
        Objects.requireNonNull(workspace, "工作目录不能为空");
        return new CodegenFactory().create(gateway,
                new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of()),
                modelId, workspace, false);
    }
}
