package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.config.AgentStorageConfiguration;
import com.dingbang.myworld.agent.memory.MemorySummary;
import com.dingbang.myworld.agent.orchestration.PlanEvent;
import com.dingbang.myworld.agent.orchestration.PlanStepStatus;
import com.dingbang.myworld.agent.persistence.RecoveryJournal;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.session.SessionRepository;
import com.dingbang.myworld.agent.session.Session;
import com.dingbang.myworld.agent.tool.ToolRegistry;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用专用 MySQL 测试库验证 MyBatis-Plus Mapper 和 Service 的持久化链路。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class MybatisMysqlPersistenceTest {
    /**
     * 验证真实 MySQL 的迁移、跨上下文查询、唯一键与恢复状态。
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "MYWORLD_TEST_MYSQL_URL", matches = ".+")
    void persistsRunsAndRecoveryAcrossContexts() {
        /** 隔离本次集成测试的标识。 */
        String suffix = UUID.randomUUID().toString();
        /** 已提交的会话标识。 */
        String sessionId;
        /** 已完成的运行标识。 */
        String runId;
        try (ConfigurableApplicationContext context = context()) {
            /** 真实 MySQL 会话 Service。 */
            SessionRepository sessions = context.getBean(SessionRepository.class);
            /** 真实 MySQL 运行 Service。 */
            RecoveryJournal journal = (RecoveryJournal) context.getBean(com.dingbang.myworld.agent.persistence.RunJournal.class);
            /** 固定模型结果的脚本入口。 */
            ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
                listener.onEvent(new TurnCompleted(new ModelTurn(message("answer", Role.ASSISTANT, "已完成"),
                        ModelFinishReason.STOP)));
                listener.onComplete();
            });
            /** 使用 MyBatis Service 的 Agent。 */
            DefaultAgentService service = service(gateway, sessions, journal);
            sessionId = service.createSession("owner-" + suffix, "app", "assistant", ModelOptions.empty());
            /** 第一次运行结果。 */
            AgentResult result = service.run(new AgentRequest("owner-" + suffix, "app", "assistant",
                    sessionId, "request-" + suffix, "第一问", ModelOptions.empty()));
            runId = result.getRunId();
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
            assertThat(journal.events(runId, "owner-" + suffix, "app", 0))
                    .extracting(event -> event.getType()).contains(AgentEventType.COMPLETED);
            assertThat(service.getSession("owner-" + suffix, "app", "assistant", sessionId).getMessages())
                    .hasSize(2);
            /** 第一个会话句柄。 */
            Session firstLease = sessions.find(sessionId);
            /** 同一会话的第二个句柄。 */
            Session secondLease = sessions.find(sessionId);
            assertThat(firstLease.tryStart()).isTrue();
            assertThat(secondLease.tryStart()).isFalse();
            firstLease.updateSummary(1, 0, new MemorySummary("第一问已完成", 2));
            assertThatThrownBy(() -> firstLease.appendExchange(0,
                    List.of(message("old-user", Role.USER, "旧问"),
                            message("old-answer", Role.ASSISTANT, "旧答"))))
                    .isInstanceOf(IllegalStateException.class).hasMessage("VERSION_CONFLICT");
            firstLease.release();
            assertThat(secondLease.tryStart()).isTrue();
            secondLease.release();
            journal.artifact(runId, "CREATE", "A.java", "abc123");
            assertThatThrownBy(() -> journal.start(UUID.randomUUID().toString(), sessionId,
                    new AgentRequest("owner-" + suffix, "app", "assistant", sessionId,
                            "request-" + suffix, "重复", ModelOptions.empty()), "model", "hash", null))
                    .isInstanceOf(IllegalStateException.class).hasMessage("REQUEST_ALREADY_EXISTS");
        }
        try (ConfigurableApplicationContext context = context()) {
            /** 重启后重新装配的运行 Service。 */
            RecoveryJournal journal = (RecoveryJournal) context.getBean(com.dingbang.myworld.agent.persistence.RunJournal.class);
            assertThat(journal.result(runId, "owner-" + suffix, "app").getFinalText()).isEqualTo("已完成");
            assertThat(journal.find(runId, "other", "app")).isNull();
            /** 重启后重新装配的会话 Service。 */
            SessionRepository sessions = context.getBean(SessionRepository.class);
            assertThat(sessions.find(sessionId).snapshot().getMessages()).hasSize(2);
            assertThat(sessions.find(sessionId).snapshot().getSummary().getText()).isEqualTo("第一问已完成");
            assertThat(journal.artifacts(runId, "owner-" + suffix, "app")).hasSize(1);
            /** 独立于已完成运行的中断工具调用。 */
            String priorRunId = UUID.randomUUID().toString();
            /** 原运行请求。 */
            AgentRequest original = new AgentRequest("owner-" + suffix, "app", "assistant", sessionId,
                    "prior-" + suffix, "读取", ModelOptions.empty());
            journal.start(priorRunId, sessionId, original, "model", "hash", null);
            /** 原工具调用。 */
            ToolCall call = new ToolCall("call-" + suffix, "read_file", "{\"path\":\"a.txt\"}");
            journal.checkpoint(priorRunId, sessionId, original, 1, 2,
                    List.of(message("user-" + suffix, Role.USER, "读取"),
                            new Message("assistant-" + suffix, Role.ASSISTANT, List.of(),
                                    List.of(call), List.of(), Map.of())));
            journal.toolStarted(priorRunId, call);
            journal.toolCompleted(priorRunId, new ToolResult(call.getCallId(), ToolResultStatus.SUCCESS,
                    "文件内容", null, false));
            assertThat(journal.interruptAbandoned()).contains(priorRunId);
            assertThat(journal.find(priorRunId, "owner-" + suffix, "app").status())
                    .isEqualTo(AgentResultStatus.INTERRUPTED);
            assertThat(journal.recoveryCheckpoint(priorRunId).exchange()).last()
                    .extracting(message -> message.getToolResults().get(0).getContent()).isEqualTo("文件内容");
            /** 从完成步骤边界恢复的计划运行。 */
            String planRunId = UUID.randomUUID().toString();
            /** 计划请求。 */
            AgentRequest planRequest = new AgentRequest("owner-" + suffix, "app", "assistant", sessionId,
                    "plan-" + suffix, "两步任务", ModelOptions.empty());
            journal.start(planRunId, sessionId, planRequest, "model", "hash", null);
            /** 原计划工具调用。 */
            ToolCall planCall = new ToolCall("plan-call-" + suffix, "create_plan",
                    "{\"name\":\"示例\",\"steps\":[\"第一步\",\"第二步\"]}");
            journal.checkpoint(planRunId, sessionId, planRequest, 1, 2,
                    List.of(message("plan-user-" + suffix, Role.USER, "两步任务"),
                            new Message("plan-assistant-" + suffix, Role.ASSISTANT, List.of(),
                                    List.of(planCall), List.of(), Map.of())));
            journal.toolStarted(planRunId, planCall);
            journal.event(new AgentEvent(planRunId, sessionId, 1, Instant.now(), AgentEventType.PLAN_CREATED,
                    null, null, null, null, new PlanEvent("示例", 0, 2, null, "两步任务")));
            journal.event(new AgentEvent(planRunId, sessionId, 2, Instant.now(), AgentEventType.PLAN_STEP_STARTED,
                    null, null, null, null, new PlanEvent("示例", 1, 2, PlanStepStatus.RUNNING, "第一步")));
            journal.event(new AgentEvent(planRunId, sessionId, 3, Instant.now(), AgentEventType.PLAN_STEP_FINISHED,
                    null, null, null, null, new PlanEvent("示例", 1, 2, PlanStepStatus.SUCCEEDED, "第一步产物")));
            assertThat(journal.interruptAbandoned()).contains(planRunId);
            assertThat(journal.find(planRunId, "owner-" + suffix, "app").status())
                    .isEqualTo(AgentResultStatus.INTERRUPTED);
            assertThat(journal.planRecovery(planRunId).statuses())
                    .containsExactly(PlanStepStatus.SUCCEEDED, PlanStepStatus.PENDING);
            /** 结果未知的外部命令。 */
            String uncertainRunId = UUID.randomUUID().toString();
            journal.start(uncertainRunId, sessionId, new AgentRequest("owner-" + suffix, "app", "assistant",
                    sessionId, "uncertain-" + suffix, "命令", ModelOptions.empty()), "model", "hash", null);
            journal.toolStarted(uncertainRunId, new ToolCall("command-" + suffix,
                    "execute_command", "{\"command\":\"touch a\"}"));
            assertThat(journal.interruptAbandoned()).contains(uncertainRunId);
            assertThat(journal.find(uncertainRunId, "owner-" + suffix, "app").status())
                    .isEqualTo(AgentResultStatus.NEEDS_REVIEW);
        }
    }

    /**
     * 创建仅装配 Agent MySQL 存储的 Spring 上下文。
     *
     * @return 可关闭的应用上下文
     */
    private ConfigurableApplicationContext context() {
        return new SpringApplicationBuilder(MysqlTestConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "my-world.agent.storage.type=mysql",
                        "my-world.agent.storage.url=" + System.getenv("MYWORLD_TEST_MYSQL_URL"),
                        "my-world.agent.storage.username=" + System.getenv().getOrDefault("MYWORLD_TEST_MYSQL_USER", "root"),
                        "my-world.agent.storage.password=" + System.getenv().getOrDefault("MYWORLD_TEST_MYSQL_PASSWORD", ""))
                .run();
    }

    /**
     * 绑定数据库 Service 和固定 Agent 定义。
     *
     * @param gateway 脚本模型
     * @param sessions 会话仓库
     * @param journal 运行日志
     * @return Agent 服务
     */
    private static DefaultAgentService service(ScriptedAgentModelGateway gateway,
                                               SessionRepository sessions, RecoveryJournal journal) {
        /** 固定 Agent 定义。 */
        AgentDefinition definition = new AgentDefinition("app", "assistant", "测试助手", "测试",
                "model", null, List.of(), List.of(), 8);
        return new DefaultAgentService(gateway,
                new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of()),
                List.of(definition), new ToolRegistry(List.of()), List.of(), AgentExecutors.WORK,
                sessions, journal);
    }

    /**
     * 创建完整的文本消息。
     *
     * @param id 消息标识
     * @param role 角色
     * @param text 文本
     * @return 文本消息
     */
    private static Message message(String id, Role role, String text) {
        return new Message(id, role, List.of(new TextContentBlock(text)), List.of(), List.of(), Map.of());
    }

    /**
     * 为真实 MySQL 集成测试启用 Spring Boot 自动配置。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(AgentStorageConfiguration.class)
    static class MysqlTestConfiguration { }
}
