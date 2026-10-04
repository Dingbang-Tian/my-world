package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentLimits;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelTokenUsage;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.api.event.UsageReported;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 S08 运行取消、超时、预算和唯一终态的实际行为。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class AgentRunControlTest {
    /**
     * 验证完整回答后迟到网络错误和重复取消不会覆盖成功结果。
     *
     * @throws Exception 等待异步运行失败时
     */
    @Test
    void completedRunIgnoresLateErrorAndCancel() throws Exception {
        // 在完成回调后故意再次报错的模型。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            textTurn(listener, "ok", null);
            listener.onError(new IllegalStateException("迟到的连接关闭"));
        });
        // 本次运行。
        AgentRun run = service(gateway, limits(4, 2, 100, Duration.ofSeconds(2)), Collections.emptyList())
                .prepare(input(null));
        // 完整事件观察者。
        RecordingAgentEventListener observer = new RecordingAgentEventListener();
        run.subscribe(observer);
        run.execute();
        // 唯一最终结果。
        AgentResult result = run.getResult().toCompletableFuture().get(2, TimeUnit.SECONDS);
        run.cancel();
        run.cancel();
        assertThat(observer.awaitCompletion(2, TimeUnit.SECONDS)).isTrue();
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(observer.getEvents()).extracting(AgentEvent::getType).containsExactly(AgentEventType.COMPLETED);
    }

    /**
     * 验证取消传播到模型令牌、唯一终态和下一次会话运行。
     *
     * @throws Exception 等待异步运行失败时
     */
    @Test
    void cancelPropagatesToModelAndReleasesSession() throws Exception {
        // 确认模型已经开始工作的门闩。
        CountDownLatch entered = new CountDownLatch(1);
        // 确认模型资源收到取消的门闩。
        CountDownLatch cancelled = new CountDownLatch(1);
        // 只在首轮等待取消的脚本模型。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (request.getMessages().size() == 2 && entered.getCount() > 0) {
                request.getExecutionContext().getCancellation().onCancel(cancelled::countDown);
                entered.countDown();
                return;
            }
            textTurn(listener, "next", null);
        });
        // 首轮运行。
        DefaultAgentService service = service(gateway, limits(4, 2, 100, Duration.ofSeconds(2)),
                Collections.emptyList());
        AgentRun run = service.prepare(input(null));
        run.execute();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        run.cancel();
        run.cancel();
        assertThat(run.getResult().toCompletableFuture().get(2, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.CANCELLED);
        assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(run::execute).isInstanceOf(IllegalStateException.class);
        // 复用已释放会话的新运行。
        AgentRun next = service.prepare(input(run.getSessionId()));
        next.execute();
        assertThat(next.getResult().toCompletableFuture().get(2, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.COMPLETED);
    }

    /**
     * 验证事件观察者抛错只结束该订阅，不改变模型最终结果。
     *
     * @throws Exception 等待运行结束失败时
     */
    @Test
    void observerFailureDoesNotChangeRun() throws Exception {
        // 模型完整回答。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            listener.onEvent(new TextDelta("ok"));
            textTurn(listener, "ok", null);
        });
        // 本次运行。
        AgentRun run = service(gateway, limits(4, 2, 100, Duration.ofSeconds(2)),
                Collections.emptyList()).prepare(input(null));
        // 观察者错误已经回报的门闩。
        CountDownLatch observerFailed = new CountDownLatch(1);
        run.subscribe(new FaultingAgentEventListener(observerFailed));
        run.execute();
        assertThat(run.getResult().toCompletableFuture().get(2, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(observerFailed.await(2, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 验证无模型回调仍由全局截止时间完成 Future。
     *
     * @throws Exception 等待超时终态失败时
     */
    @Test
    void timeoutCompletesWithoutModelCallback() throws Exception {
        // 返回后不产生任何模型回调的网关。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> { });
        // 50 毫秒全局时限的运行。
        AgentRun run = service(gateway, limits(4, 2, 100, Duration.ofMillis(50)), Collections.emptyList())
                .prepare(input(null));
        run.execute();
        // 截止时间触发的结果。
        AgentResult result = run.getResult().toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.TIMED_OUT);
        assertThat(result.getError().getCode()).isEqualTo("TIMEOUT");
        assertThat(gateway.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证取消发生在第一工具执行中时不再启动同批第二工具。
     *
     * @throws Exception 等待工具和结果失败时
     */
    @Test
    void cancellationStopsRemainingToolCalls() throws Exception {
        // 第一工具开始后的同步门闩。
        CountDownLatch firstStarted = new CountDownLatch(1);
        // 测试工具累计真实执行次数。
        AtomicInteger calls = new AtomicInteger();
        // 会等待运行取消的真实 Java 工具。
        AgentRunControlTestCountingTool tool = new AgentRunControlTestCountingTool(calls, firstStarted, true);
        // 请求同批两个工具调用的模型。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            // 完整工具调用助手消息。
            Message assistant = new Message("calls", Role.ASSISTANT, Collections.emptyList(), Arrays.asList(
                    new ToolCall("c1", "count", "{\"unused\":\"x\"}"),
                    new ToolCall("c2", "count", "{\"unused\":\"x\"}")),
                    Collections.emptyList(), Collections.emptyMap());
            listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.TOOL_CALLS)));
            listener.onComplete();
        });
        // 本次运行。
        AgentRun run = service(gateway, limits(4, 2, 100, Duration.ofSeconds(2)),
                Collections.singletonList(tool)).prepare(input(null));
        run.execute();
        assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
        run.cancel();
        assertThat(run.getResult().toCompletableFuture().get(2, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.CANCELLED);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(gateway.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证工具数量在执行前拦截，输出和用量只按完整回合计一次。
     *
     * @throws Exception 等待结果失败时
     */
    @Test
    void enforcesToolAndOutputLimitsAndCountsUsageOnce() throws Exception {
        // 未达到授权数量的工具执行计数。
        AtomicInteger calls = new AtomicInteger();
        // 超出工具预算的一批调用模型。
        ScriptedAgentModelGateway toolsGateway = new ScriptedAgentModelGateway((request, listener) -> {
            // 两个模型工具调用。
            Message assistant = new Message("calls", Role.ASSISTANT, Collections.emptyList(), Arrays.asList(
                    new ToolCall("c1", "count", "{\"unused\":\"x\"}"),
                    new ToolCall("c2", "count", "{\"unused\":\"x\"}")),
                    Collections.emptyList(), Collections.emptyMap());
            listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.TOOL_CALLS)));
            listener.onComplete();
        });
        // 工具预算拒绝的结果。
        AgentResult toolLimit = service(toolsGateway, limits(4, 1, 100, Duration.ofSeconds(2)),
                Collections.singletonList(new AgentRunControlTestCountingTool(calls, null, false))).run(input(null));
        assertThat(toolLimit.getStatus()).isEqualTo(AgentResultStatus.LIMIT_EXCEEDED);
        assertThat(calls.get()).isZero();

        // 模型给出的单轮最终用量。
        ModelTokenUsage usage = new ModelTokenUsage(3, 2, 5, null);
        // 先发重复用量事件再发完整回合的模型。
        ScriptedAgentModelGateway outputGateway = new ScriptedAgentModelGateway((request, listener) -> {
            listener.onEvent(new TextDelta("abc"));
            listener.onEvent(new UsageReported(usage));
            listener.onEvent(new UsageReported(usage));
            textTurn(listener, "abc", usage);
        });
        // 正常额度内的运行。
        AgentRun normal = service(outputGateway, limits(4, 1, 3, Duration.ofSeconds(2)),
                Collections.emptyList()).prepare(input(null));
        // 正常用量事件观察者。
        RecordingAgentEventListener observer = new RecordingAgentEventListener();
        normal.subscribe(observer);
        normal.execute();
        // 正常结果。
        AgentResult completed = normal.getResult().toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertThat(observer.awaitCompletion(2, TimeUnit.SECONDS)).isTrue();
        assertThat(completed.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(completed.getUsage()).isEqualTo(usage);
        assertThat(observer.getEvents()).extracting(AgentEvent::getType)
                .containsExactly(AgentEventType.TEXT_DELTA, AgentEventType.USAGE, AgentEventType.COMPLETED);

        // 会超过两字符上限的运行。
        AgentResult outputLimit = service(outputGateway, limits(4, 1, 2, Duration.ofSeconds(2)),
                Collections.emptyList()).run(input(null));
        assertThat(outputLimit.getStatus()).isEqualTo(AgentResultStatus.LIMIT_EXCEEDED);
        assertThat(outputLimit.getError().getCode()).isEqualTo("LIMIT_EXCEEDED");
    }

    /**
     * 构建可信测试服务。
     *
     * @param gateway 本地脚本模型
     * @param limits 可信运行预算
     * @param tools 当前注册的工具
     * @return 运行服务
     */
    private static DefaultAgentService service(ScriptedAgentModelGateway gateway, AgentLimits limits,
                                               List<Tool<?>> tools) {
        // 默认公共模板仓库。
        PromptTemplateRegistry prompts = new PromptTemplateRegistry(new DefaultResourceLoader(),
                Collections.emptyMap(), Collections.emptyMap());
        // 具有当前限制的可信定义。
        AgentDefinition definition = new AgentDefinition("app", "control", "测试助手", "控制测试", "scripted",
                null, tools.isEmpty() ? Collections.emptyList() : Collections.singletonList("count"),
                Collections.emptyList(), limits);
        return new DefaultAgentService(gateway, prompts, Collections.singletonList(definition),
                new ToolRegistry(tools), Collections.emptyList(), AgentExecutors.WORK);
    }

    /**
     * 创建测试请求。
     *
     * @param sessionId 可选已存在会话
     * @return 请求
     */
    private static AgentRequest input(String sessionId) {
        return new AgentRequest("app", "control", sessionId, "request", "你好");
    }

    /**
     * 创建测试预算。
     *
     * @param turns 最大模型回合数
     * @param tools 最大工具次数
     * @param output 最大输出字符数
     * @param timeout 整次运行时限
     * @return 可信预算
     */
    private static AgentLimits limits(int turns, int tools, int output, Duration timeout) {
        return new AgentLimits(turns, tools, output, 32, timeout);
    }

    /**
     * 输出完整文本回合。
     *
     * @param listener 当前模型监听器
     * @param text 完整答案
     * @param usage 本轮用量，可为空
     */
    private static void textTurn(ModelEventListener listener, String text, ModelTokenUsage usage) {
        // 完整助手消息。
        Message answer = new Message("answer", Role.ASSISTANT,
                Collections.singletonList(new TextContentBlock(text)), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap());
        listener.onEvent(new TurnCompleted(new ModelTurn(answer, ModelFinishReason.STOP, usage)));
        listener.onComplete();
    }


}
