package com.dingbang.myworld.aiapp.codegen.application;

import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.aiapp.codegen.api.FileArtifact;
import lombok.Value;
import java.util.List;

/**
 * 保存代码生成任务的可观察状态。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Value
public class CodegenTaskState {
    /**
     * 运行标识。
     */
    String runId;
    /**
     * 会话标识。
     */
    String sessionId;
    /**
     * 当前运行状态。
     */
    AgentResultStatus status;
    /**
     * 最终结果；运行中为空。
     */
    AgentResult result;
    /**
     * 当前已记录的文件产物。
     */
    List<FileArtifact> artifacts;
}
