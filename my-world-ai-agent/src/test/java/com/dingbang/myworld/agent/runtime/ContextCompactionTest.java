package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentLimits;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.memory.ContextAssembler;
import com.dingbang.myworld.agent.memory.ContextPolicy;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.session.InMemorySessionRepository;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolDescriptor;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证工具上下文裁剪后仍可继续运行，且用户要求和原始会话不丢失。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
class ContextCompactionTest {
    /**
     * 强制触发工具循环中的裁剪，并验证追加消息及最终完整历史提交。
     */
    @Test
    void continuesAfterCompactionWithoutChangingStoredHistory() {
        /** 较长用户要求，不允许被按字符截断。 */
        String task = "u".repeat(2000) + " KEEP_THIS_CONSTRAINT";
        /** 模型回合计数。 */
        AtomicInteger turn = new AtomicInteger();
        /** 三次工具调用后返回最终回答的本地模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            assertThat(request.getMessages().stream().filter(m -> m.getRole() == Role.SYSTEM).count()).isEqualTo(1);
            assertThat(((TextContentBlock) request.getMessages().get(1).getContentBlocks().get(0)).getText()).isEqualTo(task);
            /** 当前模型回合。 */
            int current = turn.incrementAndGet();
            /** 模型输出的工具调用或最终回答。 */
            Message answer = current <= 3
                    ? new Message("a" + current, Role.ASSISTANT, List.of(),
                    List.of(new ToolCall("c" + current, "add", "{\"a\":1,\"b\":2}")), List.of(), Map.of())
                    : new Message("done", Role.ASSISTANT, List.of(new TextContentBlock("OK")), List.of(), List.of(), Map.of());
            listener.onEvent(new TurnCompleted(new ModelTurn(answer,
                    current <= 3 ? ModelFinishReason.TOOL_CALLS : ModelFinishReason.STOP)));
            listener.onComplete();
        });
        /** 返回大文本的工具，验证旧结果被缩短但最新结果完整。 */
        AgentToolLoopTestAddTool tool = new AgentToolLoopTestAddTool() {
            /**
             * 显式提供匿名测试工具的描述，避免依赖不会继承的类注解。
             *
             * @return 加法工具描述
             */
            @Override
            public ToolDescriptor<AgentToolLoopTestAddArgs> descriptor() {
                return ToolDescriptor.of("add", "返回测试长文本", AgentToolLoopTestAddArgs.class);
            }

            /**
             * 返回可重复的长工具结果。
             *
             * @param parameters 工具参数
             * @param context 执行上下文
             * @return 长文本
             */
            @Override
            public ToolExecutionResult execute(AgentToolLoopTestAddArgs parameters, ToolExecutionContext context) {
                return ToolExecutionResult.text("x".repeat(5000));
            }
        };
        /** 本地会话仓库。 */
        InMemorySessionRepository sessions = new InMemorySessionRepository();
        /** 使用较低阈值触发真实循环中的裁剪。 */
        AgentDefinition definition = new AgentDefinition("app", "agent", "test", "test", "scripted", null,
                List.of("add"), List.of(), AgentLimits.defaults(6), new ContextPolicy(20000, 20, 9000, 1024, 256));
        /** 可同步执行且不访问供应商的服务。 */
        DefaultAgentService service = new DefaultAgentService(gateway,
                new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of()),
                List.of(definition), new ToolRegistry(List.of(tool)), List.of(), Runnable::run, sessions);
        /** 最终运行结果。 */
        var result = service.run(new AgentRequest("app", "agent", null, "compact", task));
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(turn).hasValue(4);
        /** 最后一次传给模型的工具结果。 */
        var sent = gateway.getRequests().get(3).getMessages().stream().flatMap(m -> m.getToolResults().stream()).toList();
        assertThat(sent.get(0).getContent().length()).isLessThan(5000);
        assertThat(sent.get(2).getContent()).hasSize(5000);
        assertThat(service.getSession("app", "app", "agent", result.getSessionId()).getMessages()
                .stream().flatMap(m -> m.getToolResults().stream())).allSatisfy(r -> {
                    assertThat(r.getContent()).hasSize(5000);
                    assertThat(r.isTruncated()).isFalse();
                });
    }

    /**
     * 无法压缩的超长用户输入必须保留，并交由容量检查拒绝。
     */
    @Test
    void preservesProtectedContentAndReturnsAppendableMessages() {
        /** 超长用户要求。 */
        Message user = new Message("u", Role.USER, List.of(new TextContentBlock("u".repeat(5000))), List.of(), List.of(), Map.of());
        /** 最新工具调用批次。 */
        Message call = new Message("a", Role.ASSISTANT, List.of(), List.of(new ToolCall("c", "read", "{}")), List.of(), Map.of());
        /** 最新结果必须完整保留。 */
        Message result = new Message("t", Role.TOOL, List.of(), List.of(),
                List.of(new ToolResult("c", ToolResultStatus.SUCCESS, "r".repeat(5000), null, false)), Map.of());
        /** 输入不可变列表，输出应可继续追加。 */
        var compacted = new ContextAssembler().compact(List.of(user, call, result), List.of(), 1000);
        assertThat(compacted).containsExactly(user, call, result);
        compacted.add(new Message("next", Role.USER, List.of(new TextContentBlock("next")), List.of(), List.of(), Map.of()));
        assertThat(compacted).hasSize(4);
    }
}
