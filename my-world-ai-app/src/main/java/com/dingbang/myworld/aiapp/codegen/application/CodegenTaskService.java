package com.dingbang.myworld.aiapp.codegen.application;

import com.dingbang.myworld.agent.persistence.RecoveryRunRecord;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventListener;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.persistence.RecoveryJournal;
import com.dingbang.myworld.agent.persistence.RunJournal;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.aiapp.codegen.api.CodegenService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 为代码生成应用管理运行入口、幂等请求和按归属查询。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class CodegenTaskService {
    /**
     * 已装配的代码生成应用。
     */
    private final CodegenService codegen;
    /**
     * 可选的持久化运行查询。
     */
    private final RecoveryJournal recovery;
    /**
     * 本进程创建的运行句柄。
     */
    private final Map<String, AgentRun> runs = new ConcurrentHashMap<>();
    /**
     * 本进程按所有者和请求标识登记的运行。
     */
    private final Map<String, CodegenLocalRequest> requests = new ConcurrentHashMap<>();

    /**
     * 创建运行协调服务。
     *
     * @param codegen 代码生成应用
     * @param journal 可选持久化日志
     */
    public CodegenTaskService(CodegenService codegen, RunJournal journal) {
        this.codegen = Objects.requireNonNull(codegen, "代码生成服务不能为空");
        this.recovery = journal instanceof RecoveryJournal persisted ? persisted : null;
    }

    /**
     * 创建或复用同一所有者的幂等任务。
     *
     * @param ownerId 可信所有者
     * @param sessionId 已有会话，可为空
     * @param requestId 幂等请求标识
     * @param task 用户任务
     * @return 运行标识和状态
     * @throws IllegalStateException 请求标识已用于不同输入时
     */
    public synchronized CodegenTaskState create(String ownerId, String sessionId, String requestId, String task) {
        requireId(ownerId, "所有者");
        requireId(requestId, "请求标识");
        if (task == null || task.isBlank() || task.length() > 10000) {
            throw new IllegalArgumentException("任务内容必须为 1 到 10000 个字符");
        }
        // 当前请求在进程中的唯一键。
        String key = key(ownerId, requestId);
        // 已在当前进程登记的请求。
        CodegenLocalRequest local = requests.get(key);
        if (local != null) {
            if (!Objects.equals(local.getTask(), task)
                    || (sessionId != null && !sessionId.equals(local.getSessionId()))) {
                throw new IllegalStateException("REQUEST_ID_CONFLICT");
            }
            return status(ownerId, local.getRunId());
        }
        if (recovery != null) {
            // 已持久化的幂等请求。
            RecoveryRunRecord previous = recovery.findByRequestId(ownerId, CodegenService.APP_ID, requestId);
            if (previous != null) {
                if (!previous.getUserText().equals(task)
                        || (sessionId != null && !sessionId.equals(previous.getSessionId()))) {
                    throw new IllegalStateException("REQUEST_ID_CONFLICT");
                }
                return status(ownerId, previous.getRunId());
            }
        }
        // 尚未运行的 Agent 句柄。
        AgentRun run = codegen.prepare(ownerId, sessionId, requestId, task);
        runs.put(run.getRunId(), run);
        requests.put(key, new CodegenLocalRequest(run.getRunId(), run.getSessionId(), task));
        run.execute();
        return status(ownerId, run.getRunId());
    }

    /**
     * 从可恢复的中断任务创建或复用新运行。
     *
     * @param ownerId 可信所有者
     * @param priorRunId 原运行
     * @param requestId 新请求幂等标识
     * @return 新运行状态
     * @throws IllegalStateException 原运行不可安全恢复或请求标识冲突时
     */
    public synchronized CodegenTaskState resume(String ownerId, String priorRunId, String requestId) {
        requireId(ownerId, "所有者");
        requireId(priorRunId, "原运行标识");
        requireId(requestId, "请求标识");
        // 当前进程中的幂等请求。
        CodegenLocalRequest local = requests.get(key(ownerId, requestId));
        if (local != null) {
            if (!priorRunId.equals(local.getPriorRunId())) throw new IllegalStateException("REQUEST_ID_CONFLICT");
            return status(ownerId, local.getRunId());
        }
        if (recovery != null) {
            // 已持久化的恢复运行。
            RecoveryRunRecord previous = recovery.findByRequestId(ownerId, CodegenService.APP_ID, requestId);
            if (previous != null) {
                if (!priorRunId.equals(previous.getResumedFromRunId())) {
                    throw new IllegalStateException("REQUEST_ID_CONFLICT");
                }
                return status(ownerId, previous.getRunId());
            }
        }
        // 已校验恢复边界的新运行。
        AgentRun run = codegen.resume(ownerId, priorRunId, requestId);
        runs.put(run.getRunId(), run);
        requests.put(key(ownerId, requestId), new CodegenLocalRequest(run.getRunId(), run.getSessionId(), null, priorRunId));
        run.execute();
        return status(ownerId, run.getRunId());
    }

    /**
     * 查询运行状态和当前文件产物。
     *
     * @param ownerId 可信所有者
     * @param runId 运行标识
     * @return 运行状态
     * @throws IllegalArgumentException 运行不存在或归属不匹配时
     */
    public CodegenTaskState status(String ownerId, String runId) {
        // 当前进程的运行。
        AgentRun run = ownedRun(ownerId, runId);
        // 已持久化的运行记录。
        RecoveryRunRecord record = recovery == null ? null
                : recovery.find(runId, ownerId, CodegenService.APP_ID);
        if (run == null && record == null) throw new IllegalArgumentException("未知的运行标识或归属不匹配");
        // 已完成的最终结果。
        AgentResult result = run == null ? recovery.result(runId, ownerId, CodegenService.APP_ID)
                : run.getResult().toCompletableFuture().getNow(null);
        // 当前可观察的运行状态。
        AgentResultStatus state = result != null ? result.getStatus()
                : record != null ? record.getStatus() : AgentResultStatus.RUNNING;
        // 可信记录中的会话标识。
        String sessionId = run != null ? run.getSessionId() : record.getSessionId();
        return new CodegenTaskState(runId, sessionId, state, result, codegen.artifacts(ownerId, runId));
    }

    /**
     * 读取指定所有者的代码生成会话快照。
     *
     * @param ownerId 可信所有者
     * @param sessionId 会话标识
     * @return 会话版本、消息和摘要快照
     */
    public SessionSnapshot session(String ownerId, String sessionId) {
        return codegen.session(ownerId, sessionId);
    }

    /**
     * 按归属订阅事件，当前进程实时回放，历史运行从存储分页回放。
     *
     * @param ownerId 可信所有者
     * @param runId 运行标识
     * @param afterSequence 已接收的最后序号
     * @param listener 事件监听器
     * @throws IllegalArgumentException 事件序号无效或运行归属不匹配时
     */
    public void subscribe(String ownerId, String runId, long afterSequence, AgentEventListener listener) {
        if (afterSequence < 0) throw new IllegalArgumentException("事件序号不能为负数");
        Objects.requireNonNull(listener, "事件监听器不能为空");
        // 当前进程的运行。
        AgentRun run = ownedRun(ownerId, runId);
        if (run != null) {
            run.subscribe(listener, afterSequence);
            return;
        }
        if (recovery == null || recovery.find(runId, ownerId, CodegenService.APP_ID) == null) {
            throw new IllegalArgumentException("未知的运行标识或归属不匹配");
        }
        // 已消费的事件序号。
        long cursor = afterSequence;
        while (true) {
            // 当前持久化事件页。
            List<AgentEvent> page = recovery.events(runId, ownerId, CodegenService.APP_ID, cursor);
            // 当前页中按序到达的持久化事件。
            for (AgentEvent event : page) {
                listener.onEvent(event);
                cursor = event.getSequence();
            }
            if (page.size() < 1000) break;
        }
        listener.onComplete();
    }

    /**
     * 取消当前进程中归属于所有者的运行。
     *
     * @param ownerId 可信所有者
     * @param runId 运行标识
     * @return 取消后的运行状态
     * @throws IllegalStateException 运行不属于当前进程时
     */
    public CodegenTaskState cancel(String ownerId, String runId) {
        // 当前进程的运行。
        AgentRun run = ownedRun(ownerId, runId);
        if (run == null) throw new IllegalStateException("RUN_NOT_ACTIVE");
        run.cancel();
        return status(ownerId, runId);
    }

    /**
     * 校验当前进程运行的归属。
     *
     * @param ownerId 可信所有者
     * @param runId 运行标识
     * @return 所属运行；不在当前进程或不归属时为空
     */
    private AgentRun ownedRun(String ownerId, String runId) {
        // 当前进程的运行。
        AgentRun run = runs.get(runId);
        if (run == null) return null;
        // 已登记的所有者请求。
        boolean owned = requests.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith(ownerId + "\u0000")
                && entry.getValue().getRunId().equals(runId));
        return owned ? run : null;
    }

    /**
     * 创建无歧义的所有者请求键。
     *
     * @param ownerId 所有者
     * @param requestId 请求标识
     * @return 组合键
     */
    private String key(String ownerId, String requestId) {
        return ownerId + "\u0000" + requestId;
    }

    /**
     * 校验外部标识的基本长度。
     *
     * @param value 标识内容
     * @param name 标识名称
     */
    private void requireId(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 256 || value.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException(name + "不能为空或超过 256 个字符");
        }
    }


}
