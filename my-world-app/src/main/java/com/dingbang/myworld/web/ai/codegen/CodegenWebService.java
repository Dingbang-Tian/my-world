package com.dingbang.myworld.web.ai.codegen;

import com.dingbang.myworld.agent.api.AgentEventListener;
import com.dingbang.myworld.aiapp.codegen.application.CodegenTaskService;
import com.dingbang.myworld.aiapp.codegen.application.CodegenTaskState;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 为代码生成 Web 入口封装可信所有者和任务操作。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Component
@ConditionalOnProperty(prefix = "my-world.codegen", name = "enabled", havingValue = "true")
public final class CodegenWebService {

    /**
     * 代码生成任务协调服务。
     */
    private final CodegenTaskService tasks;

    /**
     * 当前 Web 入口使用的可信所有者键。
     */
    private final String ownerKey;

    /**
     * 创建绑定服务端所有者配置的 Web 服务。
     *
     * @param tasks 代码生成任务协调服务
     * @param properties Web 入口配置
     * @throws IllegalArgumentException 所有者标识为空时
     */
    public CodegenWebService(CodegenTaskService tasks, CodegenWebProperties properties) {
        this.tasks = Objects.requireNonNull(tasks, "代码生成任务服务不能为空");
        this.ownerKey = Objects.requireNonNull(properties, "代码生成 Web 配置不能为空").getOwnerKey();
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("代码生成 Web owner-key 不能为空");
        }
    }

    /**
     * 创建或复用代码生成任务。
     *
     * @param sessionId 已有会话标识，可为空
     * @param requestId 幂等请求标识
     * @param task 用户任务
     * @return 当前任务状态
     */
    public CodegenTaskState create(String sessionId, String requestId, String task) {
        return tasks.create(ownerKey, sessionId, requestId, task);
    }

    /**
     * 查询代码生成任务状态。
     *
     * @param runId 运行标识
     * @return 当前任务状态
     */
    public CodegenTaskState status(String runId) {
        return tasks.status(ownerKey, runId);
    }

    /**
     * 查询当前 Web 所有者可见的会话快照。
     *
     * @param sessionId 会话标识
     * @return 会话版本、消息和摘要快照
     */
    public SessionSnapshot session(String sessionId) {
        return tasks.session(ownerKey, sessionId);
    }

    /**
     * 订阅代码生成任务事件。
     *
     * @param runId 运行标识
     * @param afterSequence 已接收的最后事件序号
     * @param listener 事件监听器
     */
    public void subscribe(String runId, long afterSequence, AgentEventListener listener) {
        tasks.subscribe(ownerKey, runId, afterSequence, listener);
    }

    /**
     * 取消代码生成任务。
     *
     * @param runId 运行标识
     * @return 取消后的任务状态
     */
    public CodegenTaskState cancel(String runId) {
        return tasks.cancel(ownerKey, runId);
    }

    /**
     * 从安全检查点恢复代码生成任务。
     *
     * @param runId 原运行标识
     * @param requestId 新请求标识
     * @return 新运行状态
     */
    public CodegenTaskState resume(String runId, String requestId) {
        return tasks.resume(ownerKey, runId, requestId);
    }
}
