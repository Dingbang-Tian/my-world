package com.dingbang.myworld.aiapp.codegen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 代码生成应用的可信模型、目录与写权限配置。
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
}
