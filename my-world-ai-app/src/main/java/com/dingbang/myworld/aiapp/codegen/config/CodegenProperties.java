package com.dingbang.myworld.aiapp.codegen.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 代码生成应用的可信模型、目录和工具权限配置。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "my-world.codegen")
public final class CodegenProperties {
    /**
     * 已配置的模型标识。
     */
    private String modelId;
    /**
     * 工作目录绝对路径。
     */
    private String workspace;
    /**
     * 是否允许四个写文件工具。
     */
    private boolean writeEnabled;
    /**
     * 是否允许命令执行工具。
     */
    private boolean commandEnabled;
    /**
     * 是否允许模型创建顺序计划。
     */
    private boolean planEnabled;
    /**
     * 是否允许模型创建独立子 Agent。
     */
    private boolean subAgentEnabled;
    /**
     * 可信模型上下文窗口的估算 token 数。
     */
    private int contextWindowTokens = 32768;
    /**
     * 未覆盖轮次达到此值时摘要。
     */
    private int summaryTriggerRounds = 20;
    /**
     * 输入估算达到此值时摘要。
     */
    private int summaryTriggerTokens = 24576;
    /**
     * 为最终回答预留的 token 数。
     */
    private int reserveOutputTokens = 4096;
    /**
     * 单次摘要最多允许的估算 token 数。
     */
    private int maxSummaryTokens = 1024;
    /**
     * 命令进程可继承的环境变量名称。
     */
    private List<String> commandEnvironmentAllowlist = List.of("PATH", "JAVA_HOME", "LANG", "TMPDIR");

}
