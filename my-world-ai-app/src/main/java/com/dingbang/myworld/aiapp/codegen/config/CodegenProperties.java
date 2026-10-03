package com.dingbang.myworld.aiapp.codegen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/**
 * 代码生成应用的可信模型、目录和工具权限配置。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@ConfigurationProperties(prefix = "my-world.codegen")
public final class CodegenProperties {
    /** 已配置的模型标识。 */
    private String modelId;
    /** 工作目录绝对路径。 */
    private String workspace;
    /** 是否允许四个写文件工具。 */
    private boolean writeEnabled;
    /** 是否允许命令执行工具。 */
    private boolean commandEnabled;
    /** 是否允许模型创建顺序计划。 */
    private boolean planEnabled;
    /** 是否允许模型创建独立子 Agent。 */
    private boolean subAgentEnabled;
    /** 命令进程可继承的环境变量名称。 */
    private List<String> commandEnvironmentAllowlist = List.of("PATH", "JAVA_HOME", "LANG", "TMPDIR");

    /**
     * 返回模型标识。
     *
     * @return 模型标识
     */
    public String getModelId() {
        return modelId;
    }

    /**
     * 设置模型标识。
     *
     * @param modelId 模型标识
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    /**
     * 返回工作目录。
     *
     * @return 工作目录
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * 设置工作目录。
     *
     * @param workspace 工作目录
     */
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    /**
     * 返回是否启用写工具。
     *
     * @return 写工具开关
     */
    public boolean isWriteEnabled() {
        return writeEnabled;
    }

    /**
     * 设置写工具开关。
     *
     * @param writeEnabled 是否启用
     */
    public void setWriteEnabled(boolean writeEnabled) {
        this.writeEnabled = writeEnabled;
    }

    /**
     * 返回命令工具开关。
     *
     * @return 启用时为 true
     */
    public boolean isCommandEnabled() {
        return commandEnabled;
    }

    /**
     * 设置命令工具开关。
     *
     * @param commandEnabled 是否授权命令执行
     */
    public void setCommandEnabled(boolean commandEnabled) {
        this.commandEnabled = commandEnabled;
    }

    /**
     * 返回顺序计划工具开关。
     *
     * @return 启用时为 true
     */
    public boolean isPlanEnabled() {
        return planEnabled;
    }

    /**
     * 设置顺序计划工具开关。
     *
     * @param planEnabled 是否授权计划创建
     */
    public void setPlanEnabled(boolean planEnabled) {
        this.planEnabled = planEnabled;
    }

    /**
     * 返回子 Agent 委派工具开关。
     *
     * @return 启用时为 true
     */
    public boolean isSubAgentEnabled() {
        return subAgentEnabled;
    }

    /**
     * 设置子 Agent 委派工具开关。
     *
     * @param subAgentEnabled 是否启用
     */
    public void setSubAgentEnabled(boolean subAgentEnabled) {
        this.subAgentEnabled = subAgentEnabled;
    }

    /**
     * 返回子进程可继承的环境变量名称。
     *
     * @return 名称列表
     */
    public List<String> getCommandEnvironmentAllowlist() {
        return commandEnvironmentAllowlist;
    }

    /**
     * 设置子进程可继承的环境变量名称。
     *
     * @param commandEnvironmentAllowlist 名称列表
     */
    public void setCommandEnvironmentAllowlist(List<String> commandEnvironmentAllowlist) {
        this.commandEnvironmentAllowlist = commandEnvironmentAllowlist;
    }
}
