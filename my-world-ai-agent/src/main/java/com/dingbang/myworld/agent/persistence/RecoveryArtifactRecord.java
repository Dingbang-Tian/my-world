package com.dingbang.myworld.agent.persistence;

import lombok.Value;

/**
 * 保存不包含文件内容的产物元数据。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Value
public class RecoveryArtifactRecord {
    /**
     * 运行标识。
     */
    String runId;
    /**
     * 文件操作类型。
     */
    String operation;
    /**
     * 工作区内的相对路径。
     */
    String path;
    /**
     * 操作前的文件哈希，可为空。
     */
    String beforeHash;
    /**
     * 操作后的文件哈希，可为空。
     */
    String afterHash;
}
