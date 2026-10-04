package com.dingbang.myworld.aiapp.codegen.application;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * 保存本进程的请求幂等关联。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Value
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class CodegenLocalRequest {
    /**
     * 运行标识。
     */
    String runId;
    /**
     * 会话标识。
     */
    String sessionId;
    /**
     * 普通任务原文；恢复运行时为空。
     */
    String task;
    /**
     * 恢复来源；普通运行时为空。
     */
    String priorRunId;

    /**
     * 创建普通请求的关联。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param task 用户任务
     */
    CodegenLocalRequest(String runId, String sessionId, String task) {
        this(runId, sessionId, task, null);
    }
}
