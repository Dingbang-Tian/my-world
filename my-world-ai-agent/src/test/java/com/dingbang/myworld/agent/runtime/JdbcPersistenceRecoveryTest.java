package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentError;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.memory.MemorySummary;
import com.dingbang.myworld.agent.orchestration.CreatePlanTool;
import com.dingbang.myworld.agent.orchestration.PlanEvent;
import com.dingbang.myworld.agent.orchestration.PlanStepStatus;
import com.dingbang.myworld.agent.persistence.jdbc.AgentJdbcDatabase;
import com.dingbang.myworld.agent.persistence.jdbc.JdbcRunJournal;
import com.dingbang.myworld.agent.persistence.jdbc.JdbcSessionRepository;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.session.Session;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolDescriptor;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 H2 文件库跨实例会话恢复、租约和未知副作用恢复边界。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class JdbcPersistenceRecoveryTest {
    /** 临时 H2 文件目录。 */
    @TempDir Path directory;

    /**
     * 验证独立服务实例继续会话并保留摘要与历史顺序。
     */
    @Test
    void restoresHistoryAndSummaryAcrossInstances() {
        /** 文件数据库地址。 */
        String url = "jdbc:h2:file:" + directory.resolve("session");
        /** 首次启动的数据库。 */
        AgentJdbcDatabase firstDb = new AgentJdbcDatabase(url, "sa", "");
        /** 本地确定性模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            listener.onEvent(new TurnCompleted(new ModelTurn(message("answer-" + System.nanoTime(),
                    Role.ASSISTANT, "回答"), ModelFinishReason.STOP)));
            listener.onComplete();
        });
        /** 第一实例的服务。 */
        DefaultAgentService first = service(gateway, firstDb);
        /** 持久化会话标识。 */
        String sessionId = first.createSession("owner", "app", "assistant", ModelOptions.empty());
        /** 第一轮持久化结果。 */
        com.dingbang.myworld.agent.api.AgentResult firstResult = first.run(new AgentRequest("owner", "app",
                "assistant", sessionId, "request-1", "第一问", ModelOptions.empty()));
        assertThat(firstResult.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        /** 已提交的会话。 */
        Session session = new JdbcSessionRepository(firstDb).find(sessionId);
        assertThat(session.tryStart()).isTrue();
        session.updateSummary(1, 0, new MemorySummary("第一问已回答", 2));
        session.release();
        /** 模拟进程重启后的新连接和服务。 */
        AgentJdbcDatabase secondDb = new AgentJdbcDatabase(url, "sa", "");
        DefaultAgentService second = service(gateway, secondDb);
        assertThat(new JdbcRunJournal(secondDb).result(firstResult.getRunId(), "owner", "app"))
                .isEqualTo(firstResult);
        assertThat(new JdbcRunJournal(secondDb).events(firstResult.getRunId(), "owner", "app", 0))
                .extracting(com.dingbang.myworld.agent.api.AgentEvent::getType)
                .contains(com.dingbang.myworld.agent.api.AgentEventType.COMPLETED);
        assertThat(second.getSession("owner", "app", "assistant", sessionId).getSummary().getText())
                .isEqualTo("第一问已回答");
        assertThat(second.run(new AgentRequest("owner", "app", "assistant", sessionId,
                "request-2", "追问", ModelOptions.empty())).getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(second.getSession("owner", "app", "assistant", sessionId).getMessages()).hasSize(4);
        assertThatThrownBy(() -> second.getSession("other", "app", "assistant", sessionId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new JdbcRunJournal(secondDb).find("missing", "owner", "app")).isNull();
    }

    /**
     * 验证短租约、版本冲突及请求键唯一约束。
     */
    @Test
    void rejectsConcurrentLeaseAndDuplicateRequest() {
        /** 文件数据库。 */
        AgentJdbcDatabase database = new AgentJdbcDatabase("jdbc:h2:file:" + directory.resolve("lease"), "sa", "");
        /** 会话仓库。 */
        JdbcSessionRepository repository = new JdbcSessionRepository(database);
        /** 第一会话句柄。 */
        Session first = repository.create("s", "owner", "app", "assistant", ModelOptions.empty());
        /** 第二会话句柄。 */
        Session second = repository.find("s");
        assertThat(first.tryStart()).isTrue();
        assertThat(second.tryStart()).isFalse();
        first.appendExchange(0, List.of(message("u", Role.USER, "问"), message("a", Role.ASSISTANT, "答")));
        assertThatThrownBy(() -> first.appendExchange(0,
                List.of(message("u2", Role.USER, "问"), message("a2", Role.ASSISTANT, "答"))))
                .isInstanceOf(IllegalStateException.class).hasMessage("VERSION_CONFLICT");
        first.release();
        assertThat(second.tryStart()).isTrue();
        second.release();
        /** 运行日志。 */
        JdbcRunJournal journal = new JdbcRunJournal(database);
        /** 首个请求。 */
        AgentRequest request = new AgentRequest("owner", "app", "assistant", "s", "request", "问", ModelOptions.empty());
        journal.start("run-1", "s", request, "model", "hash", null);
        assertThatThrownBy(() -> journal.start("run-2", "s", request, "model", "hash", null))
                .isInstanceOf(IllegalStateException.class).hasMessage("REQUEST_ALREADY_EXISTS");
    }

    /**
     * 验证已登记但结果未写入的工具不会被自动恢复执行。
     */
    @Test
    void marksUnknownToolOutcomeForReview() {
        /** 文件数据库。 */
        AgentJdbcDatabase database = new AgentJdbcDatabase("jdbc:h2:file:" + directory.resolve("recovery"), "sa", "");
        /** 会话仓库。 */
        JdbcSessionRepository sessions = new JdbcSessionRepository(database);
        sessions.create("s", "owner", "app", "assistant", ModelOptions.empty());
        /** 运行日志。 */
        JdbcRunJournal journal = new JdbcRunJournal(database);
        /** 原始请求。 */
        AgentRequest request = new AgentRequest("owner", "app", "assistant", "s", "r1", "任务", ModelOptions.empty());
        journal.start("run-1", "s", request, "model", "hash", null);
        journal.toolStarted("run-1", new ToolCall("call-1", "execute_command", "{\"command\":\"touch x\"}"));
        assertThat(journal.interruptAbandoned()).containsExactly("run-1");
        assertThat(journal.find("run-1", "owner", "app").status()).isEqualTo(AgentResultStatus.NEEDS_REVIEW);
        /** 恢复尝试不会再次启动未知结果的命令。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((modelRequest, listener) -> {
            throw new AssertionError("未知副作用不应调用模型");
        });
        assertThatThrownBy(() -> service(gateway, database).resume("run-1", "owner", "app", "retry"))
                .isInstanceOf(IllegalStateException.class).hasMessage("RUN_NOT_RESUMABLE");
        /** 无工具的另一中断运行。 */
        journal.start("run-2", "s", new AgentRequest("owner", "app", "assistant", "s", "r2", "任务",
                ModelOptions.empty()), "model", "hash", null);
        assertThat(journal.interruptAbandoned()).containsExactly("run-2");
        assertThat(journal.find("run-2", "owner", "app").status()).isEqualTo(AgentResultStatus.INTERRUPTED);
    }

    /**
     * 验证单实例启动扫描立即解除旧租约并保留产物元数据。
     */
    @Test
    void startupScanReleasesOldLeaseAndKeepsArtifacts() {
        /** 文件数据库。 */
        AgentJdbcDatabase database = new AgentJdbcDatabase("jdbc:h2:file:" + directory.resolve("startup"), "sa", "");
        /** 会话句柄。 */
        Session session = new JdbcSessionRepository(database).create("s", "owner", "app", "assistant",
                ModelOptions.empty());
        assertThat(session.tryStart()).isTrue();
        /** 运行日志。 */
        JdbcRunJournal journal = new JdbcRunJournal(database);
        journal.start("run", "s", new AgentRequest("owner", "app", "assistant", "s", "r", "任务",
                ModelOptions.empty()), "model", "hash", null);
        journal.artifact("run", "CREATE", "A.java", "abc123");
        assertThat(journal.interruptOnStartup()).containsExactly("run");
        assertThat(session.isBusy()).isFalse();
        assertThat(journal.find("run", "owner", "app").status()).isEqualTo(AgentResultStatus.INTERRUPTED);
        assertThat(journal.artifacts("run", "owner", "app")).hasSize(1);
        assertThatThrownBy(() -> journal.artifacts("run", "other", "app"))
                .isInstanceOf(IllegalArgumentException.class);
        /** 取消任务不参与下一次启动扫描。 */
        journal.start("cancelled", "s", new AgentRequest("owner", "app", "assistant", "s", "cancel",
                "停止", ModelOptions.empty()), "model", "hash", null);
        journal.finish(new AgentResult("cancelled", "s", "cancel", AgentResultStatus.CANCELLED,
                null, null, new AgentError("CANCELLED", "已取消"), "agent/system", "hash"));
        assertThat(journal.interruptOnStartup()).isEmpty();
        assertThat(journal.find("cancelled", "owner", "app").status()).isEqualTo(AgentResultStatus.CANCELLED);
        session.release();
    }

    /**
     * 验证结果已保存但消息尚未检查点时，从确定结果继续模型而不重复工具。
     *
     * @throws Exception 等待恢复运行失败时
     */
    @Test
    void resumesWithConfirmedToolResultWithoutRepeatingIt() throws Exception {
        /** 文件数据库。 */
        AgentJdbcDatabase database = new AgentJdbcDatabase("jdbc:h2:file:" + directory.resolve("resume"), "sa", "");
        /** 会话仓库。 */
        JdbcSessionRepository sessions = new JdbcSessionRepository(database);
        sessions.create("s", "owner", "app", "assistant", ModelOptions.empty());
        /** 原请求。 */
        AgentRequest original = new AgentRequest("owner", "app", "assistant", "s", "original", "读取文件",
                ModelOptions.empty());
        /** 原运行日志。 */
        JdbcRunJournal journal = new JdbcRunJournal(database);
        journal.start("prior", "s", original, "model", "hash", null);
        /** 工具调用消息。 */
        ToolCall call = new ToolCall("call", "view_file", "{\"path\":\"a.txt\"}");
        Message assistantCall = new Message("prior:assistant:1", Role.ASSISTANT, List.of(), List.of(call),
                List.of(), Map.of());
        journal.checkpoint("prior", "s", original, 0, 2,
                List.of(message("prior:user", Role.USER, "读取文件"), assistantCall));
        journal.toolStarted("prior", call);
        journal.toolCompleted("prior", new ToolResult("call", ToolResultStatus.SUCCESS, "文件内容", null, false));
        assertThat(journal.interruptAbandoned()).containsExactly("prior");
        /** 恢复模型只应看到已保存的工具结果。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            assertThat(request.getMessages()).extracting(Message::getRole)
                    .containsExactly(Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.TOOL);
            assertThat(request.getMessages().get(3).getToolResults().get(0).getContent()).isEqualTo("文件内容");
            listener.onEvent(new TurnCompleted(new ModelTurn(message("answer", Role.ASSISTANT, "已读取"),
                    ModelFinishReason.STOP)));
            listener.onComplete();
        });
        /** 关联来源的显式恢复运行。 */
        AgentRun resumed = service(gateway, database).resume("prior", "owner", "app", "resumed");
        resumed.execute();
        assertThat(resumed.getResult().toCompletableFuture().get(5, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(sessions.find("s").snapshot().getMessages()).hasSize(4);
        assertThat(journal.find(resumed.getRunId(), "owner", "app").resumedFromRunId()).isEqualTo("prior");
    }

    /**
     * 验证计划从已确认的步骤边界继续，并将前一步报告提供给下一步。
     *
     * @throws Exception 等待恢复运行失败时
     */
    @Test
    void resumesPlanFromCompletedStepBoundary() throws Exception {
        /** 文件数据库。 */
        AgentJdbcDatabase database = new AgentJdbcDatabase("jdbc:h2:file:" + directory.resolve("plan"), "sa", "");
        new JdbcSessionRepository(database).create("s", "owner", "app", "assistant", ModelOptions.empty());
        /** 原请求及运行日志。 */
        AgentRequest original = new AgentRequest("owner", "app", "assistant", "s", "original", "做两步任务",
                ModelOptions.empty());
        JdbcRunJournal journal = new JdbcRunJournal(database);
        journal.start("prior", "s", original, "model", "hash", null);
        /** 原计划调用。 */
        ToolCall call = new ToolCall("plan-call", "create_plan", "{\"name\":\"示例\",\"description\":\"两步任务\","
                + "\"steps\":[\"第一步\",\"第二步\"],\"failurePolicy\":\"STOP\"}");
        journal.checkpoint("prior", "s", original, 0, 2, List.of(message("prior:user", Role.USER, "做两步任务"),
                new Message("prior:assistant", Role.ASSISTANT, List.of(), List.of(call), List.of(), Map.of())));
        journal.toolStarted("prior", call);
        journal.event(new AgentEvent("prior", "s", 1, Instant.now(), AgentEventType.PLAN_CREATED,
                null, null, null, null, new PlanEvent("示例", 0, 2, null, "两步任务")));
        journal.event(new AgentEvent("prior", "s", 2, Instant.now(), AgentEventType.PLAN_STEP_STARTED,
                null, null, null, null, new PlanEvent("示例", 1, 2, PlanStepStatus.RUNNING, "第一步")));
        journal.event(new AgentEvent("prior", "s", 3, Instant.now(), AgentEventType.PLAN_STEP_FINISHED,
                null, null, null, null, new PlanEvent("示例", 1, 2, PlanStepStatus.SUCCEEDED, "第一步产物")));
        assertThat(journal.interruptAbandoned()).containsExactly("prior");
        assertThat(journal.find("prior", "owner", "app").status()).isEqualTo(AgentResultStatus.INTERRUPTED);
        /** 恢复后只执行第二步和最终总结。 */
        AtomicInteger calls = new AtomicInteger();
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (calls.incrementAndGet() == 1) {
                assertThat(request.getMessages().stream().flatMap(message -> message.getContentBlocks().stream())
                        .filter(TextContentBlock.class::isInstance).map(TextContentBlock.class::cast)
                        .map(TextContentBlock::getText).toList())
                        .anyMatch(text -> text.contains("步骤 1 [SUCCEEDED]: 第一步产物"));
                listener.onEvent(new TurnCompleted(new ModelTurn(message("step2", Role.ASSISTANT, "第二步完成"),
                        ModelFinishReason.STOP)));
            } else {
                listener.onEvent(new TurnCompleted(new ModelTurn(message("final", Role.ASSISTANT, "全部完成"),
                        ModelFinishReason.STOP)));
            }
            listener.onComplete();
        });
        /** 授权计划工具的当前服务。 */
        PromptTemplateRegistry prompts = new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of());
        AgentDefinition definition = new AgentDefinition("app", "assistant", "测试助手", "测试", "model", null,
                List.of("create_plan"), List.of(), 8);
        DefaultAgentService service = new DefaultAgentService(gateway, prompts, List.of(definition),
                new ToolRegistry(List.of(new CreatePlanTool())), List.of(), AgentExecutors.WORK,
                new JdbcSessionRepository(database), journal);
        AgentRun resumed = service.resume("prior", "owner", "app", "resumed");
        resumed.execute();
        assertThat(resumed.getResult().toCompletableFuture().get(5, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(journal.events(resumed.getRunId(), "owner", "app", 0))
                .extracting(AgentEvent::getType).contains(AgentEventType.PLAN_STEP_FINISHED, AgentEventType.PLAN_FINISHED);
    }

    /**
     * 验证外部副作用工具抛异常时运行立即进入核查状态，不让模型再次调用工具。
     */
    @Test
    void stopsAfterUncertainSideEffectException() {
        /** 文件数据库。 */
        AgentJdbcDatabase database = new AgentJdbcDatabase("jdbc:h2:file:" + directory.resolve("uncertain"),
                "sa", "");
        /** 工具副作用是否已经发生。 */
        AtomicBoolean changed = new AtomicBoolean();
        /** 只提出一次写工具调用的模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            listener.onEvent(new TurnCompleted(new ModelTurn(new Message("call", Role.ASSISTANT, List.of(),
                    List.of(new ToolCall("c1", "risky_write", "{\"content\":\"x\"}")), List.of(), Map.of()),
                    ModelFinishReason.TOOL_CALLS)));
            listener.onComplete();
        });
        /** 可信工具定义。 */
        AgentDefinition definition = new AgentDefinition("app", "assistant", "测试助手", "测试", "model", null,
                List.of("risky_write"), List.of(), 8);
        /** 启用数据库的服务。 */
        DefaultAgentService service = new DefaultAgentService(gateway,
                new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of()), List.of(definition),
                new ToolRegistry(List.of(new RiskyTool(changed))), List.of(), AgentExecutors.WORK,
                new JdbcSessionRepository(database), new JdbcRunJournal(database));
        /** 不确定副作用运行结果。 */
        AgentResult result = service.run(new AgentRequest("owner", "app", "assistant", null,
                "risky", "写入", ModelOptions.empty()));
        assertThat(changed).isTrue();
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.NEEDS_REVIEW);
        assertThat(gateway.getCallCount()).isEqualTo(1);
        assertThat(new JdbcRunJournal(database).find(result.getRunId(), "owner", "app").status())
                .isEqualTo(AgentResultStatus.NEEDS_REVIEW);
    }

    /**
     * 在抛出异常前模拟已发生外部修改的测试工具。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    private static final class RiskyTool implements Tool<RiskyArgs> {
        /** 模拟副作用标记。 */
        private final AtomicBoolean changed;

        /**
         * 绑定副作用标记。
         *
         * @param changed 副作用状态
         */
        private RiskyTool(AtomicBoolean changed) { this.changed = changed; }

        /** @return 工具参数类 */
        @Override public Class<RiskyArgs> parameterType() { return RiskyArgs.class; }
        /** @return 可信工具描述 */
        @Override public ToolDescriptor<RiskyArgs> descriptor() {
            return ToolDescriptor.of("risky_write", "模拟写入后失败", RiskyArgs.class);
        }
        /** @return 工具有外部副作用时为 true */
        @Override public boolean mayHaveExternalSideEffects() { return true; }
        /**
         * 修改状态后模拟写入失败。
         *
         * @param parameters 已校验参数
         * @param context 运行上下文
         * @return 不会正常返回
         */
        @Override public ToolExecutionResult execute(RiskyArgs parameters, ToolExecutionContext context) {
            changed.set(true);
            throw new IllegalStateException("写入后异常");
        }
    }

    /**
     * 风险工具的模型可填写参数。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public static final class RiskyArgs {
        /** 测试内容。 */
        @ToolParam(description = "测试内容")
        public String content;
    }

    /**
     * 创建使用同一数据库的服务实例。
     *
     * @param gateway 本地模型
     * @param database 已迁移数据库
     * @return 服务
     */
    private static DefaultAgentService service(ScriptedAgentModelGateway gateway, AgentJdbcDatabase database) {
        /** 提示词仓库。 */
        PromptTemplateRegistry prompts = new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of());
        /** 可信 Agent 定义。 */
        AgentDefinition definition = new AgentDefinition("app", "assistant", "测试助手", "测试", "model", null);
        return new DefaultAgentService(gateway, prompts, List.of(definition), new ToolRegistry(List.of()),
                List.of(), AgentExecutors.WORK, new JdbcSessionRepository(database), new JdbcRunJournal(database));
    }

    /**
     * 创建文本消息。
     *
     * @param id 消息标识
     * @param role 消息角色
     * @param text 文本
     * @return 消息
     */
    private static Message message(String id, Role role, String text) {
        return new Message(id, role, List.of(new TextContentBlock(text)), List.of(), List.of(), Map.of());
    }
}
