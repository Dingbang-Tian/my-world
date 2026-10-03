package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentLimits;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.memory.ContextPolicy;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.session.InMemorySessionRepository;
import com.dingbang.myworld.agent.session.Session;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.api.ModelTokenUsage;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 S15 摘要阈值、完整交换、窗口错误和导出恢复。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class ContextMemoryTest {
    /**
     * 验证轮次触发后保留原始历史、回注摘要、记录用量和导出恢复。
     *
     * @throws Exception 等待运行或事件失败时
     */
    @Test
    void compressesRoundsAndRestoresSummary() throws Exception {
        /** 脚本模型，摘要请求返回短记忆。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (isSummary(request.getMessages())) complete(listener, "记住目标", new ModelTokenUsage(10, 3, 13, null));
            else complete(listener, "回答", new ModelTokenUsage(5, 2, 7, null));
        });
        /** 会话仓库。 */
        InMemorySessionRepository repository = new InMemorySessionRepository();
        /** 两轮触发的服务。 */
        DefaultAgentService service = service(gateway, repository, new ContextPolicy(2048, 2, 1800, 128, 128));
        /** 会话标识。 */
        String id = service.createSession("owner", "app", "agent", ModelOptions.empty());
        assertThat(run(service, id, "目标一").getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(run(service, id, "目标二").getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        /** 第三轮运行。 */
        AgentRun third = service.prepare(request(id, "最新输入"));
        /** 压缩事件观察者。 */
        RecordingAgentEventListener events = new RecordingAgentEventListener();
        third.subscribe(events);
        third.execute();
        /** 含摘要用量的运行结果。 */
        AgentResult result = third.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(result.getUsage().getTotalTokens()).isEqualTo(20);
        assertThat(events.awaitCompletion(3, TimeUnit.SECONDS)).isTrue();
        assertThat(events.getEvents()).extracting(event -> event.getType())
                .contains(AgentEventType.MEMORY_COMPRESSED);
        /** 保留原始历史的快照。 */
        SessionSnapshot snapshot = service.getSession("owner", "app", "agent", id);
        assertThat(snapshot.getMessages()).hasSize(6);
        assertThat(snapshot.getSummary().getCoveredMessageCount()).isEqualTo(4);
        /** 摘要后的正常请求。 */
        List<Message> context = gateway.getRequests().get(3).getMessages();
        assertThat(context).extracting(Message::getRole).containsExactly(Role.SYSTEM, Role.SYSTEM, Role.USER);
        assertThat(text(context.get(1))).contains("记住目标");
        assertThat(text(context.get(2))).isEqualTo("最新输入");
        /** 带覆盖位置的导出 JSON。 */
        String serialized = service.exportSession("owner", "app", "agent", id);
        assertThat(serialized).contains("memorySummary", "coveredMessageCount");
        /** 使用新仓库恢复的服务。 */
        DefaultAgentService restored = service(gateway, new InMemorySessionRepository(),
                new ContextPolicy(2048, 2, 1800, 128, 128));
        restored.importSession("owner", "app", "agent", serialized);
        assertThat(restored.getSession("owner", "app", "agent", id).getSummary()).isEqualTo(snapshot.getSummary());
        /** 篡改为交换中间位置的导出内容。 */
        String broken = serialized.replace("\"coveredMessageCount\":4", "\"coveredMessageCount\":3");
        /** 用于验证导入拒绝的独立服务。 */
        DefaultAgentService invalidTarget = service(gateway, new InMemorySessionRepository(),
                new ContextPolicy(2048, 2, 1800, 128, 128));
        assertThatThrownBy(() -> invalidTarget.importSession("owner", "app", "agent", broken))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(run(restored, id, "恢复后追问").getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getRequests().get(4).getMessages()).extracting(Message::getRole)
                .containsExactly(Role.SYSTEM, Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.USER);
    }

    /**
     * 验证 token 阈值可单独触发且工具调用与结果完整进入摘要。
     */
    @Test
    void tokenThresholdKeepsToolExchangeWhole() {
        /** 记录摘要内容的模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) ->
                complete(listener, isSummary(request.getMessages()) ? "调用已完成" : "继续", null));
        /** 存放带工具交换的仓库。 */
        InMemorySessionRepository repository = new InMemorySessionRepository();
        /** 已创建会话。 */
        Session session = repository.create("s", "owner", "app", "agent", ModelOptions.empty());
        assertThat(session.tryStart()).isTrue();
        session.appendExchange(0, List.of(message("u", Role.USER, "请计算一段较长的内容"),
                new Message("a1", Role.ASSISTANT, List.of(),
                        List.of(new ToolCall("c1", "calculate", "{\"value\":123}")), List.of(), Map.of()),
                new Message("t1", Role.TOOL, List.of(), List.of(),
                        List.of(new ToolResult("c1", ToolResultStatus.SUCCESS, "123", null, false)), Map.of()),
                message("a2", Role.ASSISTANT, "计算完成")));
        session.release();
        /** 高轮次阈值、低 token 阈值的服务。 */
        DefaultAgentService service = service(gateway, repository, new ContextPolicy(2048, 20, 100, 128, 128));
        assertThat(run(service, "s", "继续").getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getRequests()).hasSize(2);
        assertThat(text(gateway.getRequests().get(0).getMessages().get(0)))
                .contains("c1", "calculate", "123");
        assertThat(service.getSession("owner", "app", "agent", "s").getSummary().getCoveredMessageCount())
                .isEqualTo(4);
    }

    /**
     * 验证分块摘要只重复回注前次摘要，不重复加入已经覆盖的原始交换。
     */
    @Test
    void multipleSummaryCallsAdvanceCoverageWithoutRepeatingRawMessages() {
        /** 摘要回复中携带当前调用序号的模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) ->
                complete(listener, isSummary(request.getMessages()) ? "已有摘要" : "回答", null));
        /** 存放三段长交换的仓库。 */
        InMemorySessionRepository repository = new InMemorySessionRepository();
        /** 待压缩会话。 */
        Session session = repository.create("multi", "owner", "app", "agent", ModelOptions.empty());
        /** 三段连续交换的索引。 */
        for (int index = 0; index < 3; index++) {
            assertThat(session.tryStart()).isTrue();
            session.appendExchange(index, List.of(message("u" + index, Role.USER,
                    "MARKER" + index + "x".repeat(190)), message("a" + index, Role.ASSISTANT,
                    "y".repeat(190))));
            session.release();
        }
        /** 小窗口摘要服务。 */
        DefaultAgentService service = service(gateway, repository, new ContextPolicy(800, 1, 650, 64, 64));
        assertThat(run(service, "multi", "最新问题").getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        /** 已发出的摘要请求数量。 */
        long calls = gateway.getRequests().stream().filter(request -> isSummary(request.getMessages())).count();
        assertThat(calls).isGreaterThan(1);
        /** 最后一次摘要提示。 */
        String lastPrompt = text(gateway.getRequests().get((int) calls - 1).getMessages().get(0));
        assertThat(lastPrompt).contains("既有摘要", "MARKER2").doesNotContain("MARKER0");
        assertThat(service.getSession("owner", "app", "agent", "multi").getSummary().getCoveredMessageCount())
                .isEqualTo(6);
    }

    /**
     * 验证摘要调用失败和过长输出均不改变原始历史与覆盖位置。
     */
    @Test
    void failureAndOversizeLeaveHistoryUntouched() {
        /** 是否让摘要模型返回超长文本。 */
        for (boolean oversized : List.of(false, true)) {
            /** 摘要失败或返回超长文本的模型。 */
            ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
                if (isSummary(request.getMessages()) && !oversized) listener.onError(new IllegalStateException("故障"));
                else complete(listener, isSummary(request.getMessages()) ? "超".repeat(100) : "完成", null);
            });
            /** 本次独立服务。 */
            DefaultAgentService service = service(gateway, new InMemorySessionRepository(),
                    new ContextPolicy(2048, 1, 1800, 128, 32));
            /** 已创建会话。 */
            String id = service.createSession("owner", "app", "agent", ModelOptions.empty());
            assertThat(run(service, id, "第一轮").getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
            assertThat(run(service, id, "第二轮").getStatus()).isEqualTo(AgentResultStatus.FAILED);
            assertThat(service.getSession("owner", "app", "agent", id).getMessages()).hasSize(2);
            assertThat(service.getSession("owner", "app", "agent", id).getSummary()).isNull();
        }
    }

    /**
     * 验证最新用户输入超过窗口时返回明确限额错误，且不会调用模型。
     */
    @Test
    void refusesOversizedLatestInput() {
        /** 不应被调用的模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) ->
                complete(listener, "错误调用", null));
        /** 小窗口服务。 */
        DefaultAgentService service = service(gateway, new InMemorySessionRepository(),
                new ContextPolicy(512, 20, 300, 128, 64));
        /** 超过窗口的请求结果。 */
        AgentResult result = service.run(new AgentRequest("owner", "app", "agent", null, "oversized",
                "长".repeat(300), ModelOptions.empty()));
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.LIMIT_EXCEEDED);
        assertThat(result.getError().getMessage()).contains("CONTEXT_WINDOW_EXCEEDED");
        assertThat(gateway.getCallCount()).isZero();
    }

    /**
     * 验证摘要请求占用模型回合预算，必须为正常回答留下至少一轮。
     */
    @Test
    void summaryRequiresAnAdditionalModelTurn() {
        /** 用于确认没有发出第二次模型请求的脚本模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) ->
                complete(listener, "回答", null));
        /** 只允许一次模型调用的服务。 */
        DefaultAgentService service = service(gateway, new InMemorySessionRepository(),
                new ContextPolicy(2048, 1, 1800, 128, 128), 1);
        /** 已创建会话。 */
        String id = service.createSession("owner", "app", "agent", ModelOptions.empty());
        assertThat(run(service, id, "第一轮").getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(run(service, id, "第二轮").getStatus()).isEqualTo(AgentResultStatus.LIMIT_EXCEEDED);
        assertThat(gateway.getCallCount()).isEqualTo(1);
        assertThat(service.getSession("owner", "app", "agent", id).getSummary()).isNull();
    }

    /**
     * 创建绑定策略的本地 Agent 服务。
     *
     * @param gateway 脚本模型
     * @param repository 会话仓库
     * @param policy 上下文策略
     * @return Agent 服务
     */
    private static DefaultAgentService service(ScriptedAgentModelGateway gateway,
                                               InMemorySessionRepository repository, ContextPolicy policy) {
        return service(gateway, repository, policy, 8);
    }

    /**
     * 创建具有指定模型回合预算的本地 Agent 服务。
     *
     * @param gateway 脚本模型
     * @param repository 会话仓库
     * @param policy 上下文策略
     * @param maxTurns 最大模型回合数
     * @return Agent 服务
     */
    private static DefaultAgentService service(ScriptedAgentModelGateway gateway,
                                               InMemorySessionRepository repository, ContextPolicy policy,
                                               int maxTurns) {
        /** 当前测试的可信定义。 */
        AgentDefinition definition = new AgentDefinition("app", "agent", "测试", "摘要验证", "model", null,
                List.of(), List.of(), new AgentLimits(maxTurns, 0, 8192, 64, Duration.ofSeconds(5)), policy);
        return new DefaultAgentService(gateway, new PromptTemplateRegistry(new DefaultResourceLoader(),
                Map.of(), Map.of()), List.of(definition), new ToolRegistry(List.of()), List.of(),
                Runnable::run, repository);
    }

    /**
     * 执行一个既有会话的普通请求。
     *
     * @param service Agent 服务
     * @param id 会话标识
     * @param userText 用户文本
     * @return 运行结果
     */
    private static AgentResult run(DefaultAgentService service, String id, String userText) {
        return service.run(request(id, userText));
    }

    /**
     * 创建一次既有会话请求。
     *
     * @param id 会话标识
     * @param userText 用户文本
     * @return 请求
     */
    private static AgentRequest request(String id, String userText) {
        return new AgentRequest("owner", "app", "agent", id, "request-" + userText,
                userText, ModelOptions.empty());
    }

    /**
     * 返回消息中的第一个文本块。
     *
     * @param message 待读取消息
     * @return 文本
     */
    private static String text(Message message) {
        return ((TextContentBlock) message.getContentBlocks().get(0)).getText();
    }

    /**
     * 识别没有工具的摘要提示。
     *
     * @param messages 请求消息
     * @return 摘要请求时为 true
     */
    private static boolean isSummary(List<Message> messages) {
        return messages.size() == 1 && text(messages.get(0)).contains("待压缩内容");
    }

    /**
     * 发送完整助手回合。
     *
     * @param listener 模型事件监听器
     * @param answer 回答文本
     * @param usage 模型用量，可为 null
     */
    private static void complete(ModelEventListener listener, String answer, ModelTokenUsage usage) {
        listener.onEvent(new TurnCompleted(new ModelTurn(message("assistant", Role.ASSISTANT, answer),
                ModelFinishReason.STOP, usage)));
        listener.onComplete();
    }

    /**
     * 创建普通文本消息。
     *
     * @param id 消息标识
     * @param role 消息角色
     * @param value 文本
     * @return 消息
     */
    private static Message message(String id, Role role, String value) {
        return new Message(id, role, List.of(new TextContentBlock(value)), List.of(), List.of(), Map.of());
    }
}
