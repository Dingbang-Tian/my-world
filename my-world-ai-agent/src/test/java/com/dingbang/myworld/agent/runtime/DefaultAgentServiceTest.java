package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.api.AgentService;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.prompt.PromptTemplateSpec;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证无工具 Agent 的准备、执行、事件复用和会话历史。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
class DefaultAgentServiceTest {

    /**
     * 证明准备和事件注册不调用模型，多个监听器共享唯一运行。
     *
     * @throws Exception 等待测试运行失败时
     */
    @Test
    void executesOnceAndReplaysEventsToMultipleObservers() throws Exception {
        // 脚本只返回两个增量和一个完整助手回合。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            listener.onEvent(new TextDelta("O"));
            listener.onEvent(new TextDelta("K"));
            complete(listener, "assistant-1", "OK");
        });
        // 测试用 Agent 服务。
        AgentService service = service(gateway);
        // 已准备但尚未执行的 Agent 运行。
        AgentRun run = service.prepare(new AgentRequest("app-1", "assistant", null, "request-1", "只回复 OK"));
        // 第一个事件观察者。
        RecordingAgentEventListener firstObserver = new RecordingAgentEventListener();
        // 第二个事件观察者。
        RecordingAgentEventListener secondObserver = new RecordingAgentEventListener();
        run.subscribe(firstObserver);
        run.subscribe(secondObserver);

        assertThat(gateway.getCallCount()).isZero();
        assertThat(run.getResult().toCompletableFuture()).isNotDone();
        run.execute();
        // 模型调用后的最终 Agent 结果。
        AgentResult result = run.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertThat(firstObserver.awaitCompletion(3, TimeUnit.SECONDS)).isTrue();
        assertThat(secondObserver.awaitCompletion(3, TimeUnit.SECONDS)).isTrue();
        // 第一个观察者收到的完整事件序列。
        List<AgentEvent> first = firstObserver.getEvents();
        // 第二个观察者收到的完整事件序列。
        List<AgentEvent> second = secondObserver.getEvents();

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(result.getFinalText()).isEqualTo("OK");
        assertThat(result.getFinishReason()).isEqualTo(ModelFinishReason.STOP);
        assertThat(result.getRunId()).isEqualTo(run.getRunId());
        assertThat(result.getSessionId()).isEqualTo(run.getSessionId());
        assertThat(result.getRequestId()).isEqualTo("request-1");
        assertThat(result.getPromptHash()).hasSize(64);
        assertThat(gateway.getCallCount()).isEqualTo(1);
        assertThat(first).isEqualTo(second);
        assertThat(first).extracting(AgentEvent::getType)
                .containsExactly(AgentEventType.TEXT_DELTA, AgentEventType.TEXT_DELTA, AgentEventType.COMPLETED);
        assertThat(first).extracting(AgentEvent::getSequence).containsExactly(1L, 2L, 3L);
        assertThat(first).allSatisfy(event -> {
            assertThat(event.getRunId()).isEqualTo(run.getRunId());
            assertThat(event.getSessionId()).isEqualTo(run.getSessionId());
            assertThat(event.getTimestamp()).isNotNull();
        });
        assertThat(first.get(2).getResult()).isEqualTo(result);
        // 在运行结束后注册的迟到观察者。
        RecordingAgentEventListener lateObserver = new RecordingAgentEventListener();
        run.subscribe(lateObserver);
        assertThat(lateObserver.awaitCompletion(3, TimeUnit.SECONDS)).isTrue();
        assertThat(lateObserver.getEvents()).isEqualTo(first);
        assertThatThrownBy(run::execute).isInstanceOf(IllegalStateException.class);

        // 实际发给模型的一次请求。
        ModelRequest modelRequest = gateway.getRequests().get(0);
        assertThat(modelRequest.getModelId()).isEqualTo("model-1");
        assertThat(modelRequest.getMessages()).extracting(Message::getRole)
                .containsExactly(Role.SYSTEM, Role.USER);
        assertThat(getText(modelRequest.getMessages().get(0))).contains("你是 测试助手");
        assertThat(getText(modelRequest.getMessages().get(1))).isEqualTo("只回复 OK");
        System.out.println("S04 agent events: " + first);
    }

    /**
     * 验证第二轮只读取完整助手消息，不把文本增量追加到历史。
     *
     * @throws Exception 等待测试运行失败时
     */
    @Test
    void carriesCompletedExchangeIntoNextRequest() throws Exception {
        // 每次模型调用都给出同一条完整回答。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            listener.onEvent(new TextDelta("O"));
            listener.onEvent(new TextDelta("K"));
            complete(listener, "assistant-1", "OK");
        });
        // 测试用 Agent 服务。
        AgentService service = service(gateway);
        // 第一轮运行。
        AgentRun first = service.prepare(new AgentRequest("app-1", "assistant", null, "request-1", "第一问"));
        first.execute();
        assertThat(first.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.COMPLETED);

        // 复用第一轮会话的第二轮运行。
        AgentRun second = service.prepare(new AgentRequest("app-1", "assistant", first.getSessionId(),
                "request-2", "第二问"));
        second.execute();
        assertThat(second.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.COMPLETED);

        // 第二轮实际发给模型的消息。
        List<Message> messages = gateway.getRequests().get(1).getMessages();
        assertThat(messages).extracting(Message::getRole)
                .containsExactly(Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.USER);
        assertThat(getText(messages.get(1))).isEqualTo("第一问");
        assertThat(getText(messages.get(2))).isEqualTo("OK");
        assertThat(getText(messages.get(3))).isEqualTo("第二问");
        assertThatThrownBy(() -> service.prepare(new AgentRequest(
                "other-app", "assistant", first.getSessionId(), "request-3", "越权")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证模型错误也结束结果和事件序列，且会话可以再次执行。
     *
     * @throws Exception 等待测试运行失败时
     */
    @Test
    void completesFailedRunAndReleasesSession() throws Exception {
        // 第一次调用失败，第二次在同一会话正常完成的本地模型。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if ("失败请求".equals(getText(request.getMessages().get(request.getMessages().size() - 1)))) {
                throw new IllegalStateException("模拟模型断开");
            }
            complete(listener, "assistant-2", "恢复完成");
        });
        // 测试用 Agent 服务。
        AgentService service = service(gateway);
        // 产生模型失败的第一轮运行。
        AgentRun first = service.prepare(new AgentRequest("app-1", "assistant", null, "request-1", "失败请求"));
        first.execute();
        // 失败的 Agent 结果。
        AgentResult failed = first.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertThat(failed.getStatus()).isEqualTo(AgentResultStatus.FAILED);
        assertThat(failed.getError().getCode()).isEqualTo("MODEL_FAILURE");
        // 失败运行结束后注册的事件观察者。
        RecordingAgentEventListener observer = new RecordingAgentEventListener();
        first.subscribe(observer);
        assertThat(observer.awaitCompletion(3, TimeUnit.SECONDS)).isTrue();
        assertThat(observer.getEvents()).extracting(AgentEvent::getType).containsExactly(AgentEventType.FAILED);

        // 使用同一会话正常完成的第二轮结果。
        AgentResult recovered = service.run(new AgentRequest("app-1", "assistant", first.getSessionId(),
                "request-2", "恢复请求"));
        assertThat(recovered.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(recovered.getFinalText()).isEqualTo("恢复完成");
        assertThat(gateway.getRequests().get(1).getMessages()).extracting(Message::getRole)
                .containsExactly(Role.SYSTEM, Role.USER);
    }

    /**
     * 验证未授权工具生成可识别的结果并交给模型修正。
     *
     * @throws Exception 等待测试运行失败时
     */
    @Test
    void returnsUnknownToolResultToModel() throws Exception {
        // 假模型返回的工具调用助手消息。
        Message assistant = new Message("assistant-call", Role.ASSISTANT, Collections.emptyList(),
                Collections.singletonList(new ToolCall("call-1", "add", "{\"a\":2,\"b\":3}")),
                Collections.emptyList(), Collections.emptyMap());
        // 只返回工具回合的本地模型。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (request.getMessages().size() > 2) {
                complete(listener, "assistant-fixed", "没有授权 add 工具");
                return;
            }
            listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.TOOL_CALLS)));
            listener.onComplete();
        });
        // 当前无工具运行。
        AgentRun run = service(gateway).prepare(new AgentRequest(
                "app-1", "assistant", null, "request-1", "计算 2+3"));
        run.execute();
        // 模型根据未知工具错误修正后的结果。
        AgentResult result = run.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS);

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getCallCount()).isEqualTo(2);
        assertThat(gateway.getRequests().get(0).getTools()).isEmpty();
        assertThat(gateway.getRequests().get(1).getMessages()).extracting(Message::getRole)
                .containsExactly(Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.TOOL);
        assertThat(gateway.getRequests().get(1).getMessages().get(3).getToolResults().get(0).getErrorCode())
                .isEqualTo("TOOL_NOT_FOUND");
    }

    /**
     * 验证同一会话只允许一条运行占用。
     *
     * @throws Exception 等待测试运行失败时
     */
    @Test
    void rejectsConcurrentRunOnSameSession() throws Exception {
        // 标记第一条模型调用已经启动的同步门闩。
        CountDownLatch modelStarted = new CountDownLatch(1);
        // 控制第一条模型调用何时完成的同步门闩。
        CountDownLatch allowModelCompletion = new CountDownLatch(1);
        // 会在第一条调用中等待的本地模型。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            modelStarted.countDown();
            await(allowModelCompletion);
            complete(listener, "assistant-1", "完成");
        });
        // 测试用 Agent 服务。
        AgentService service = service(gateway);
        // 持有会话的第一轮运行。
        AgentRun first = service.prepare(new AgentRequest("app-1", "assistant", null, "request-1", "等待"));
        first.execute();
        assertThat(modelStarted.await(3, TimeUnit.SECONDS)).isTrue();
        // 试图并发使用同一会话的第二轮运行。
        AgentRun second = service.prepare(new AgentRequest("app-1", "assistant", first.getSessionId(),
                "request-2", "并发请求"));
        second.execute();

        // 会话忙时返回的第二轮结果。
        AgentResult busy = second.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertThat(busy.getStatus()).isEqualTo(AgentResultStatus.FAILED);
        assertThat(busy.getError().getCode()).isEqualTo("SESSION_BUSY");
        allowModelCompletion.countDown();
        assertThat(first.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证项目模板修改后，新服务请求使用新版本且结果标出相应哈希。
     *
     * @param directory 临时项目模板目录
     * @throws IOException 临时模板写入失败时
     */
    @Test
    void usesReloadedProjectPromptInActualModelRequest(@TempDir Path directory) throws IOException {
        // 用两次服务创建模拟配置文件修改后的重启。
        Path templateFile = directory.resolve("system.md");
        Files.write(templateFile, "第一版 {{agentName}}".getBytes(StandardCharsets.UTF_8));
        // 指向临时项目模板文件的配置。
        PromptTemplateSpec spec = new PromptTemplateSpec();
        spec.setLocation(templateFile.toUri().toString());
        // 每次都返回文本完整回合的本地模型。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) ->
                complete(listener, "assistant-1", "收到"));
        // 使用临时模板的 Agent 定义。
        AgentDefinition definition = new AgentDefinition(
                "app-1", "assistant", "测试助手", "回答问题", "model-1", null);

        // 第一次重建后的提示词仓库。
        PromptTemplateRegistry firstPrompts = new PromptTemplateRegistry(new DefaultResourceLoader(),
                Collections.emptyMap(), Collections.singletonMap("agent/system", spec));
        // 使用第一版模板的 Agent 服务。
        AgentService firstService = new DefaultAgentService(gateway, firstPrompts, Collections.singletonList(definition));
        // 第一版模板产生的结果。
        AgentResult first = firstService.run(new AgentRequest("app-1", "assistant", null, "request-1", "开始"));

        Files.write(templateFile, "第二版 {{agentName}}".getBytes(StandardCharsets.UTF_8));
        // 第二次重建后的提示词仓库。
        PromptTemplateRegistry secondPrompts = new PromptTemplateRegistry(new DefaultResourceLoader(),
                Collections.emptyMap(), Collections.singletonMap("agent/system", spec));
        // 使用第二版模板的 Agent 服务。
        AgentService secondService = new DefaultAgentService(gateway, secondPrompts, Collections.singletonList(definition));
        // 第二版模板产生的结果。
        AgentResult second = secondService.run(new AgentRequest("app-1", "assistant", null, "request-2", "继续"));

        assertThat(getText(gateway.getRequests().get(0).getMessages().get(0))).isEqualTo("第一版 测试助手");
        assertThat(getText(gateway.getRequests().get(1).getMessages().get(0))).isEqualTo("第二版 测试助手");
        assertThat(first.getPromptHash()).isNotEqualTo(second.getPromptHash());
        assertThat(first.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
    }

    /**
     * 创建测试用服务与公共系统模板。
     *
     * @param gateway 本地假模型
     * @return Agent 服务
     */
    private static AgentService service(ScriptedAgentModelGateway gateway) {
        // 使用默认模板的提示词仓库。
        PromptTemplateRegistry prompts = new PromptTemplateRegistry(new DefaultResourceLoader(),
                Collections.emptyMap(), Collections.emptyMap());
        // 本应用中的测试 Agent 定义。
        AgentDefinition definition = new AgentDefinition(
                "app-1", "assistant", "测试助手", "回答简单问题", "model-1", null);
        // 用于验证会话归属的另一应用 Agent 定义。
        AgentDefinition otherApplication = new AgentDefinition(
                "other-app", "assistant", "其他助手", "只用于归属校验", "model-1", null);
        return new DefaultAgentService(gateway, prompts, Arrays.asList(definition, otherApplication));
    }

    /**
     * 将完整文本作为模型结束回合发送给监听器。
     *
     * @param listener 接收模型事件的监听器
     * @param messageId 助手消息标识
     * @param text 完整助手文本
     */
    private static void complete(ModelEventListener listener, String messageId, String text) {
        // 完整的助手回合。
        ModelTurn turn = new ModelTurn(textMessage(messageId, Role.ASSISTANT, text), ModelFinishReason.STOP);
        listener.onEvent(new TurnCompleted(turn));
        listener.onComplete();
    }

    /**
     * 等待模型测试脚本被允许继续。
     *
     * @param latch 控制模型脚本的同步门闩
     */
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待模型测试脚本超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待模型测试脚本时被中断", exception);
        }
    }

    /**
     * 创建只有文本的模型消息。
     *
     * @param messageId 消息标识
     * @param role 消息角色
     * @param text 文本内容
     * @return 模型消息
     */
    private static Message textMessage(String messageId, Role role, String text) {
        return new Message(messageId, role, Collections.singletonList(new TextContentBlock(text)),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * 提取测试消息的第一个文本块。
     *
     * @param message 模型消息
     * @return 文本内容
     */
    private static String getText(Message message) {
        return ((TextContentBlock) message.getContentBlocks().get(0)).getText();
    }
}
