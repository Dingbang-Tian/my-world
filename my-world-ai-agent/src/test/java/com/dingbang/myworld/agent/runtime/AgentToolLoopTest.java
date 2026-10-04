package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.skill.AgentSkill;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.tool.ToolExecutionPhase;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证唯一 Agent 循环中的工具配对、纠错、技能和回合限制。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
class AgentToolLoopTest {
    /**
     * 验证两个调用分别回传、技能说明和授权工具去重、后续会话保留完整历史。
     *
     * @throws Exception 等待运行结果失败时
     */
    @Test
    void pairsMultipleToolsAndPreservesHistory() throws Exception {
        // 脚本模型的当前请求编号。
        AtomicInteger step = new AtomicInteger();
        // 两次请求工具调用，第三次是新用户回合。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (step.incrementAndGet() == 1) {
                toolTurn(listener, "call-1", "{\"a\":2,\"b\":3}",
                        "call-2", "{\"a\":4,\"b\":5}");
            } else {
                textTurn(listener, "answer-" + step.get(), step.get() == 2 ? "5 和 9" : "继续");
            }
        });
        // 记录真实工具调用次数的测试工具。
        AgentToolLoopTestAddTool tool = new AgentToolLoopTestAddTool();
        // 同一 add 同时由直接授权和技能引用。
        AgentDefinition definition = definition(Arrays.asList("add"), Arrays.asList("math"), 4);
        // 使用共享工具注册表的 Agent 服务。
        DefaultAgentService service = service(gateway, definition, tool,
                Collections.singletonList(new AgentSkill("math", "遇到算术任务时调用 add", Arrays.asList("add", "add"))));
        // 当前运行。
        AgentRun run = service.prepare(new AgentRequest("app", "math", null, "r1", "计算两组加法"));
        // 记录运行事件的观察者。
        RecordingAgentEventListener observer = new RecordingAgentEventListener();
        run.subscribe(observer);
        run.execute();
        // 第一次运行的完整结果。
        AgentResult result = run.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertThat(observer.awaitCompletion(3, TimeUnit.SECONDS)).isTrue();
        // 第二个模型请求的消息历史。
        List<Message> messages = gateway.getRequests().get(1).getMessages();

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(result.getFinalText()).isEqualTo("5 和 9");
        assertThat(tool.invocations.get()).isEqualTo(2);
        assertThat(gateway.getRequests().get(0).getTools()).hasSize(1);
        assertThat(gateway.getRequests().get(1).getTools()).hasSize(1);
        assertThat(gateway.getRequests().get(0).getTools().get(0).getParameterSchemaJson())
                .contains("a", "b");
        assertThat(text(messages.get(0))).contains("遇到算术任务时调用 add");
        assertThat(messages).extracting(Message::getRole)
                .containsExactly(Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.TOOL, Role.TOOL);
        assertThat(messages.get(2).getToolCalls()).extracting(ToolCall::getCallId)
                .containsExactly("call-1", "call-2");
        assertThat(messages.get(3).getToolResults().get(0).getCallId()).isEqualTo("call-1");
        assertThat(messages.get(3).getToolResults().get(0).getContent()).isEqualTo("5");
        assertThat(messages.get(4).getToolResults().get(0).getCallId()).isEqualTo("call-2");
        assertThat(messages.get(4).getToolResults().get(0).getContent()).isEqualTo("9");
        assertThat(observer.getEvents()).extracting(AgentEvent::getType)
                .containsExactly(AgentEventType.TOOL_EXECUTION, AgentEventType.TOOL_EXECUTION,
                        AgentEventType.TOOL_EXECUTION, AgentEventType.TOOL_EXECUTION,
                        AgentEventType.TOOL_EXECUTION, AgentEventType.TOOL_EXECUTION,
                        AgentEventType.COMPLETED);
        assertThat(observer.getEvents()).extracting(AgentEvent::getSequence)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(observer.getEvents().subList(0, 6)).extracting(event -> event.getToolExecution().getPhase())
                .containsExactly(ToolExecutionPhase.PREPARING, ToolExecutionPhase.CALLING,
                        ToolExecutionPhase.COMPLETED, ToolExecutionPhase.PREPARING,
                        ToolExecutionPhase.CALLING, ToolExecutionPhase.COMPLETED);
        assertThat(observer.getEvents().get(0).getToolExecution().getCallId()).isEqualTo("call-1");
        assertThat(observer.getEvents().get(3).getToolExecution().getCallId()).isEqualTo("call-2");

        // 同一会话的后续请求。
        AgentResult followUp = service.run(new AgentRequest("app", "math", run.getSessionId(), "r2", "再说一次"));
        assertThat(followUp.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getRequests().get(2).getMessages()).extracting(Message::getRole)
                .containsExactly(Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.TOOL, Role.TOOL,
                        Role.ASSISTANT, Role.USER);
        System.out.println("S06 model trace: USER -> ASSISTANT(call-1,call-2) -> TOOL(call-1=5)"
                + " -> TOOL(call-2=9) -> ASSISTANT(5 和 9)");
    }

    /**
     * 验证参数错误不会调用 Java 工具，错误结果可让模型修正。
     *
     * @throws Exception 等待运行结果失败时
     */
    @Test
    void returnsValidationErrorForModelCorrection() throws Exception {
        // 脚本模型请求编号。
        AtomicInteger step = new AtomicInteger();
        // 先提交缺字段参数，再根据错误返回有效调用。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (step.incrementAndGet() == 1) {
                toolTurn(listener, "bad", "{\"a\":2}");
            } else if (step.get() == 2) {
                toolTurn(listener, "fixed", "{\"a\":2,\"b\":3}");
            } else {
                textTurn(listener, "answer", "5");
            }
        });
        // 计数工具。
        AgentToolLoopTestAddTool tool = new AgentToolLoopTestAddTool();
        // 运行结果。
        AgentResult result = service(gateway, definition(Collections.singletonList("add"),
                Collections.emptyList(), 4), tool, Collections.emptyList())
                .run(new AgentRequest("app", "math", null, "r1", "计算 2+3"));

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getCallCount()).isEqualTo(3);
        assertThat(tool.invocations.get()).isEqualTo(1);
        assertThat(gateway.getRequests().get(1).getMessages().get(3).getToolResults().get(0).getStatus())
                .isEqualTo(ToolResultStatus.ERROR);
        assertThat(gateway.getRequests().get(1).getMessages().get(3).getToolResults().get(0).getErrorCode())
                .isEqualTo("TOOL_VALIDATION_ERROR");
        assertThat(gateway.getRequests().get(2).getMessages().get(5).getToolResults().get(0).getStatus())
                .isEqualTo(ToolResultStatus.SUCCESS);
    }

    /**
     * 验证达到回合限制时停止，且不执行无法回传结果的下一批工具。
     *
     * @throws Exception 等待运行结果失败时
     */
    @Test
    void stopsBeforeToolsAtModelTurnLimit() throws Exception {
        // 模型每轮继续请求同一工具。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) ->
                toolTurn(listener, "call-" + (request.getMessages().size()), "{\"a\":2,\"b\":3}"));
        // 计数工具。
        AgentToolLoopTestAddTool tool = new AgentToolLoopTestAddTool();
        // 只允许两个模型回合的运行。
        AgentRun run = service(gateway, definition(Collections.singletonList("add"),
                Collections.emptyList(), 2), tool, Collections.emptyList())
                .prepare(new AgentRequest("app", "math", null, "r1", "继续调用"));
        // 记录终态事件的观察者。
        RecordingAgentEventListener observer = new RecordingAgentEventListener();
        run.subscribe(observer);
        run.execute();
        // 达到限制的结果。
        AgentResult result = run.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertThat(observer.awaitCompletion(3, TimeUnit.SECONDS)).isTrue();

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.LIMIT_EXCEEDED);
        assertThat(result.getError().getCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(gateway.getCallCount()).isEqualTo(2);
        assertThat(tool.invocations.get()).isEqualTo(1);
        assertThat(observer.getEvents()).extracting(AgentEvent::getType)
                .containsExactly(AgentEventType.TOOL_EXECUTION, AgentEventType.TOOL_EXECUTION,
                        AgentEventType.TOOL_EXECUTION, AgentEventType.LIMIT_EXCEEDED);
    }

    /**
     * 验证同一助手消息的重复调用标识被拒绝且不产生工具副作用。
     *
     * @throws Exception 等待运行结果失败时
     */
    @Test
    void rejectsDuplicateCallIdsBeforeExecution() throws Exception {
        // 返回两个相同调用标识的脚本模型。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) ->
                toolTurn(listener, "same", "{\"a\":2,\"b\":3}",
                        "same", "{\"a\":4,\"b\":5}"));
        // 计数工具。
        AgentToolLoopTestAddTool tool = new AgentToolLoopTestAddTool();
        // 模型结果。
        AgentResult result = service(gateway, definition(Collections.singletonList("add"),
                Collections.emptyList(), 3), tool, Collections.emptyList())
                .run(new AgentRequest("app", "math", null, "r1", "计算"));

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED);
        assertThat(result.getError().getCode()).isEqualTo("INVALID_MODEL_TURN");
        assertThat(tool.invocations.get()).isZero();
        assertThat(gateway.getCallCount()).isEqualTo(1);
    }

    /**
     * 创建测试服务。
     *
     * @param gateway 本地脚本模型
     * @param definition 可信 Agent 定义
     * @param tool 测试工具
     * @param skills 可用技能
     * @return Agent 服务
     */
    private static DefaultAgentService service(ScriptedAgentModelGateway gateway,
                                               AgentDefinition definition, AgentToolLoopTestAddTool tool,
                                               List<AgentSkill> skills) {
        // 公共默认系统提示词。
        PromptTemplateRegistry prompts = new PromptTemplateRegistry(new DefaultResourceLoader(),
                Collections.emptyMap(), Collections.emptyMap());
        return new DefaultAgentService(gateway, prompts, Collections.singletonList(definition),
                new ToolRegistry(Collections.singletonList(tool)), skills, ForkJoinPool.commonPool());
    }

    /**
     * 创建可信 Agent 定义。
     *
     * @param toolIds 直接授权的工具
     * @param skillIds 启用的技能
     * @param maxTurns 模型回合上限
     * @return Agent 定义
     */
    private static AgentDefinition definition(List<String> toolIds, List<String> skillIds, int maxTurns) {
        return new AgentDefinition("app", "math", "数学助手", "计算整数加法", "scripted", null,
                toolIds, skillIds, maxTurns);
    }

    /**
     * 发送工具调用模型回合。
     *
     * @param listener 模型监听器
     * @param firstId 第一调用标识
     * @param firstArgs 第一组参数
     * @param extra 第二调用的可选标识和参数
     */
    private static void toolTurn(ModelEventListener listener, String firstId, String firstArgs,
                                 String... extra) {
        // 本轮工具调用。
        List<ToolCall> calls = new java.util.ArrayList<>();
        calls.add(new ToolCall(firstId, "add", firstArgs));
        if (extra.length == 2) {
            calls.add(new ToolCall(extra[0], "add", extra[1]));
        }
        // 完整助手工具消息。
        Message assistant = new Message("assistant-" + firstId, Role.ASSISTANT, Collections.emptyList(),
                calls, Collections.emptyList(), Collections.emptyMap());
        listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.TOOL_CALLS)));
        listener.onComplete();
    }

    /**
     * 发送最终文本模型回合。
     *
     * @param listener 模型监听器
     * @param messageId 助手消息标识
     * @param answer 完整答案
     */
    private static void textTurn(ModelEventListener listener, String messageId, String answer) {
        // 最终助手消息。
        Message assistant = new Message(messageId, Role.ASSISTANT,
                Collections.singletonList(new TextContentBlock(answer)), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap());
        listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.STOP)));
        listener.onComplete();
    }

    /**
     * 读取第一段文本内容。
     *
     * @param message 模型消息
     * @return 文本内容
     */
    private static String text(Message message) {
        return ((TextContentBlock) message.getContentBlocks().get(0)).getText();
    }


}
