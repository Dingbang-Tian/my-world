package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.session.InMemorySessionRepository;
import com.dingbang.myworld.agent.session.Session;
import com.dingbang.myworld.agent.session.SessionExportCodec;
import com.dingbang.myworld.agent.session.SessionSnapshot;
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
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 S09 会话隔离、版本提交、选项及无凭据导出恢复。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class AgentSessionServiceTest {
    /**
     * 验证两位所有者及两会话的历史和选项互不泄露，并可恢复继续。
     */
    @Test
    void isolatesOwnersAndRestoresConversation() {
        /** 本地模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            /** 用户文本。 */
            String answer = ((TextContentBlock) request.getMessages().get(request.getMessages().size() - 1)
                    .getContentBlocks().get(0)).getText();
            listener.onEvent(new TurnCompleted(new ModelTurn(text("assistant-" + answer, Role.ASSISTANT, answer),
                    ModelFinishReason.STOP)));
            listener.onComplete();
        });
        /** 第一个服务。 */
        DefaultAgentService service = service(gateway, new InMemorySessionRepository());
        /** 所有者甲的会话。 */
        String first = service.createSession("owner-a", "app", "assistant", new ModelOptions(0.2, 25, null));
        /** 所有者乙的会话。 */
        String second = service.createSession("owner-b", "app", "assistant", new ModelOptions(0.8, 50, null));

        assertThat(service.run(new AgentRequest("owner-a", "app", "assistant", first, "r1", "甲的事实",
                ModelOptions.empty())).getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(service.run(new AgentRequest("owner-b", "app", "assistant", second, "r2", "乙的事实",
                ModelOptions.empty())).getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getRequests().get(0).getOptions().getTemperature()).isEqualTo(0.2);
        assertThat(gateway.getRequests().get(1).getOptions().getTemperature()).isEqualTo(0.8);
        assertThatThrownBy(() -> service.getSession("owner-b", "app", "assistant", first))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.exportSession("owner-b", "app", "assistant", first))
                .isInstanceOf(IllegalArgumentException.class);

        /** 导出的无凭据会话。 */
        String exported = service.exportSession("owner-a", "app", "assistant", first);
        assertThat(exported).contains("\"schemaVersion\":1", "甲的事实");
        assertThat(exported).doesNotContain("apiKey", "client", "secret");
        /** 使用新仓库的第二个服务。 */
        DefaultAgentService restored = service(gateway, new InMemorySessionRepository());
        /** 带有伪造工具授权字段的导入内容。 */
        String untrustedExport = exported.replace("\"messages\":", "\"toolIds\":[\"danger\"],\"messages\":");
        assertThat(restored.importSession("owner-a", "app", "assistant", untrustedExport)).isEqualTo(first);
        assertThat(restored.getSession("owner-a", "app", "assistant", first).getMessages())
                .isEqualTo(service.getSession("owner-a", "app", "assistant", first).getMessages());
        assertThat(restored.run(new AgentRequest("owner-a", "app", "assistant", first, "r3", "追问",
                new ModelOptions(0.4, null, null))).getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getRequests().get(2).getMessages()).extracting(Message::getRole)
                .containsExactly(Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.USER);
        assertThat(gateway.getRequests().get(2).getOptions().getTemperature()).isEqualTo(0.4);
        assertThat(gateway.getRequests().get(2).getOptions().getMaxCompletionTokens()).isEqualTo(25);
        assertThat(gateway.getRequests().get(2).getTools()).isEmpty();
        assertThatThrownBy(() -> restored.importSession("owner-a", "app", "assistant", exported))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> restored.importSession("owner-b", "app", "assistant", exported))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restored.importSession("owner-a", "app", "assistant",
                exported.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证导出恢复保留工具调用与结果，并拒绝孤立工具结果。
     */
    @Test
    void preservesPairedToolsAndRejectsIncompleteHistory() {
        /** 完整工具交换。 */
        List<Message> exchange = Arrays.asList(text("u", Role.USER, "计算"),
                new Message("a1", Role.ASSISTANT, List.of(),
                        List.of(new ToolCall("c1", "add", "{\"a\":2}")), List.of(), Map.of()),
                new Message("t1", Role.TOOL, List.of(), List.of(),
                        List.of(new ToolResult("c1", ToolResultStatus.SUCCESS, "2", null, false)), Map.of()),
                text("a2", Role.ASSISTANT, "2"));
        /** 内存仓库。 */
        InMemorySessionRepository repository = new InMemorySessionRepository();
        /** 会话。 */
        Session session = repository.create("id", "owner", "app", "assistant", ModelOptions.empty());
        assertThat(session.tryStart()).isTrue();
        session.appendExchange(0, exchange);
        assertThatThrownBy(() -> session.appendExchange(0, exchange))
                .isInstanceOf(IllegalStateException.class).hasMessage("VERSION_CONFLICT");
        session.release();
        /** 导出编解码器。 */
        SessionExportCodec codec = new SessionExportCodec();
        /** 导出 JSON。 */
        String json = codec.encode("id", "owner", "app", "assistant", session.options(), session.snapshot());
        assertThat(codec.decode(json).getMessages()).isEqualTo(exchange);
        assertThatThrownBy(() -> codec.decode(json.replace("\"callId\":\"c1\",\"status\"",
                "\"callId\":\"missing\",\"status\"")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证运行占用时不能导出，同会话第二个运行得到忙状态，取消后不提交交换。
     *
     * @throws Exception 等待异步模型失败时
     */
    @Test
    void busyAndCancellationDoNotPolluteHistory() throws Exception {
        /** 模型已启动信号。 */
        CountDownLatch entered = new CountDownLatch(1);
        /** 模型释放信号。 */
        CountDownLatch release = new CountDownLatch(1);
        /** 等待释放的本地模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            entered.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        /** 服务。 */
        DefaultAgentService service = service(gateway, new InMemorySessionRepository());
        /** 会话标识。 */
        String sessionId = service.createSession("owner", "app", "assistant", ModelOptions.empty());
        /** 第一个运行。 */
        AgentRun first = service.prepare(new AgentRequest("owner", "app", "assistant", sessionId,
                "r1", "未完成", ModelOptions.empty()));
        first.execute();
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> service.exportSession("owner", "app", "assistant", sessionId))
                .isInstanceOf(IllegalStateException.class).hasMessage("SESSION_BUSY");
        assertThat(service.run(new AgentRequest("owner", "app", "assistant", sessionId,
                "r2", "另一问", ModelOptions.empty())).getError().getCode()).isEqualTo("SESSION_BUSY");
        first.cancel();
        release.countDown();
        assertThat(first.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.CANCELLED);
        assertThat(service.getSession("owner", "app", "assistant", sessionId).getMessages()).isEmpty();
    }

    /**
     * 创建使用同一可信定义的服务。
     *
     * @param gateway 本地模型
     * @param repository 会话仓库
     * @return Agent 服务
     */
    private static DefaultAgentService service(ScriptedAgentModelGateway gateway, InMemorySessionRepository repository) {
        /** 提示词仓库。 */
        PromptTemplateRegistry prompts = new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of());
        /** 可信 Agent 定义。 */
        AgentDefinition definition = new AgentDefinition("app", "assistant", "测试助手", "测试", "model", null);
        return new DefaultAgentService(gateway, prompts, List.of(definition),
                new ToolRegistry(Collections.emptyList()), List.of(), AgentExecutors.WORK, repository);
    }

    /**
     * 创建文本消息。
     *
     * @param id 消息标识
     * @param role 消息角色
     * @param value 文本内容
     * @return 消息
     */
    private static Message text(String id, Role role, String value) {
        return new Message(id, role, List.of(new TextContentBlock(value)), List.of(), List.of(), Map.of());
    }
}
