package com.dingbang.myworld.aiapp.codegen.api;

import java.util.Objects;

/**
 * 记录一次文件工具成功执行后的产物路径、动作与版本。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public record FileArtifact(String runId, String action, String path, String sha256) {
    /**
     * 校验产物记录中的必需值。
     *
     * @param runId Agent 运行标识
     * @param action 创建、编辑、移动或删除动作
     * @param path 工作目录内相对路径
     * @param sha256 操作后的文件版本；删除时为删除前版本
     */
    public FileArtifact {
        Objects.requireNonNull(runId, "运行标识不能为空");
        Objects.requireNonNull(action, "动作不能为空");
        Objects.requireNonNull(path, "文件路径不能为空");
        Objects.requireNonNull(sha256, "文件版本不能为空");
    }
}
