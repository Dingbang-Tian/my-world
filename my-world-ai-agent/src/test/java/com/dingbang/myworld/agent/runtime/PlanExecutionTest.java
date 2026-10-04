package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentLimits;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.orchestration.CreatePlanTool;
import com.dingbang.myworld.agent.orchestration.PlanStepStatus;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用脚本模型验证顺序计划的上下文、状态、预算和取消。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class PlanExecutionTest {
    /**
     * 验证第二步获得第一步工具产物和显式结果，计划事件与主历史完整。
     *
     * @throws Exception 等待事件结束失败时
     */
    @Test
    void passesPriorArtifactAndReportsLifecycle() throws Exception {
        // 当前模型请求序号。
        AtomicInteger turn = new AtomicInteger();
        // 仅返回固定响应的本地模型。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            switch (turn.incrementAndGet()) {
                case 1 -> tool(listener, "plan", "create_plan", planJson("STOP"));
                case 2 -> {
                    assertThat(request.getTools()).extracting(item -> item.getName())
                            .containsExactly("artifact");
                    tool(listener, "file", "artifact", "{\"name\":\"HelloAgent.java\"}");
                }
                case 3 -> text(listener, "file-created", "生成 HelloAgent.java");
                case 4 -> {
                    assertThat(request.getMessages().stream().map(PlanExecutionTest::messageText).toList())
                            .anyMatch(value -> value.contains("步骤 1 [SUCCEEDED]: 生成 HelloAgent.java"));
                    assertThat(request.getMessages().stream().flatMap(item -> item.getToolResults().stream())
                            .map(item -> item.getContent()).toList()).contains("HelloAgent.java");
                    text(listener, "compiled", "编译已通过");
                }
                case 5 -> {
                    assertThat(request.getMessages().stream().flatMap(item -> item.getToolResults().stream())
                            .map(item -> item.getContent()).toList())
                            .anyMatch(value -> value.contains("计划 示例：SUCCEEDED"));
                    text(listener, "final", "计划完成");
                }
                default -> throw new AssertionError("多余模型回合");
            }
        });
        // 已配置计划和产物工具的服务。
        DefaultAgentService service = service(gateway, new AgentLimits(8, 8, 10000, 100,
                Duration.ofSeconds(5), 3));
        // 当前运行。
        AgentRun run = service.prepare(request());
        // 记录计划事件。
        RecordingAgentEventListener observer = new RecordingAgentEventListener();
        run.subscribe(observer);
        run.execute();
        // 最终运行结果。
        AgentResult result = run.getResult().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(observer.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getCallCount()).isEqualTo(5);
        assertThat(observer.getEvents()).extracting(AgentEvent::getType)
                .contains(AgentEventType.PLAN_CREATED, AgentEventType.PLAN_STEP_STARTED,
                        AgentEventType.PLAN_STEP_FINISHED, AgentEventType.PLAN_FINISHED);
        assertThat(observer.getEvents().stream().filter(event -> event.getType() == AgentEventType.PLAN_STEP_FINISHED)
                .map(event -> event.getPlan().getStatus()).toList())
                .containsExactly(PlanStepStatus.SUCCEEDED, PlanStepStatus.SUCCEEDED);
        assertThat(service.getSession("app", "app", "planner", run.getSessionId()).getMessages())
                .extracting(Message::getRole).contains(Role.TOOL);
        System.out.println("S13 scripted trace: create_plan -> step1 artifact -> step1 SUCCEEDED"
                + " -> step2 sees HelloAgent.java -> step2 SUCCEEDED -> plan SUCCEEDED -> final");
    }

    /**
     * 验证停止与继续策略均保留真实失败状态。
     *
     * @throws Exception 等待模型结果失败时
     */
    @Test
    void stopAndContinueDoNotClaimSuccess() throws Exception {
        for (String policy : List.of("STOP", "CONTINUE")) {
            // 当前模型请求序号。
            AtomicInteger turn = new AtomicInteger();
            // 第一步固定失败的脚本模型。
            ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
                switch (turn.incrementAndGet()) {
                    case 1 -> tool(listener, "plan", "create_plan", planJson(policy));
                    case 2 -> listener.onError(new IllegalStateException("编译失败"));
                    case 3 -> {
                        if (policy.equals("CONTINUE")) {
                            assertThat(request.getMessages().stream().map(PlanExecutionTest::messageText).toList())
                                    .anyMatch(value -> value.contains("步骤 1 [FAILED]"));
                            text(listener, "step-2", "继续检查");
                        } else {
                            assertThat(request.getMessages().stream().flatMap(item -> item.getToolResults().stream())
                                    .anyMatch(result -> result.getErrorCode() != null
                                            && result.getErrorCode().equals("PLAN_FAILED"))).isTrue();
                            text(listener, "final", "已失败");
                        }
                    }
                    case 4 -> text(listener, "final", "有一步失败");
                    default -> throw new AssertionError("多余模型回合");
                }
            });
            // 当前策略的运行结果。
            AgentResult result = service(gateway, new AgentLimits(6, 4, 10000, 100,
                    Duration.ofSeconds(5), 3)).run(request());
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED);
            assertThat(result.getError().getCode()).isEqualTo("PLAN_FAILED");
            assertThat(result.getError().getMessage()).contains("步骤 1 [FAILED]");
            assertThat(result.getError().getMessage()).contains(policy.equals("STOP")
                    ? "步骤 2 [SKIPPED]" : "步骤 2 [SUCCEEDED]");
            assertThat(gateway.getCallCount()).isEqualTo(policy.equals("STOP") ? 3 : 4);
        }
    }

    /**
     * 验证步骤上限和全局模型回合限制由程序执行。
     *
     * @throws Exception 等待模型结果失败时
     */
    @Test
    void enforcesPlanStepAndSharedTurnLimits() throws Exception {
        // 步骤数超过可信上限的脚本模型。
        ScriptedAgentModelGateway tooMany = new ScriptedAgentModelGateway((request, listener) -> {
            if (request.getMessages().stream().flatMap(item -> item.getToolResults().stream()).findAny().isPresent()) {
                text(listener, "final", "参数被拒绝");
            } else {
                tool(listener, "plan", "create_plan", planJson("STOP"));
            }
        });
        // 参数校验后的主运行结果。
        AgentResult validation = service(tooMany, new AgentLimits(3, 3, 10000, 100,
                Duration.ofSeconds(5), 1)).run(request());
        assertThat(validation.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(tooMany.getRequests().get(1).getMessages().stream()
                .flatMap(item -> item.getToolResults().stream()).findFirst().orElseThrow().getErrorCode())
                .isEqualTo("TOOL_VALIDATION_ERROR");

        // 仅允许三个模型回合的计划。
        AtomicInteger turn = new AtomicInteger();
        // 计划步骤和主任务共享回合预算的脚本模型。
        ScriptedAgentModelGateway limited = new ScriptedAgentModelGateway((request, listener) -> {
            switch (turn.incrementAndGet()) {
                case 1 -> tool(listener, "plan", "create_plan", planJson("STOP"));
                case 2 -> text(listener, "step-1", "生成完成");
                case 3 -> text(listener, "step-2", "编译完成");
                default -> throw new AssertionError("预算外模型调用");
            }
        });
        // 超预算运行的最终结果。
        AgentResult result = service(limited, new AgentLimits(3, 4, 10000, 100,
                Duration.ofSeconds(5), 3)).run(request());
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.LIMIT_EXCEEDED);
        assertThat(limited.getCallCount()).isEqualTo(3);
    }

    /**
     * 验证步骤内即使伪造计划调用也会被程序拒绝，且工具预算覆盖步骤。
     *
     * @throws Exception 等待模型结果失败时
     */
    @Test
    void blocksRecursivePlanAndSharesToolBudget() throws Exception {
        // 当前模型请求序号。
        AtomicInteger turn = new AtomicInteger();
        // 步骤内故意伪造 create_plan 的模型。
        ScriptedAgentModelGateway recursive = new ScriptedAgentModelGateway((request, listener) -> {
            switch (turn.incrementAndGet()) {
                case 1 -> tool(listener, "plan", "create_plan", planJson("STOP"));
                case 2 -> {
                    assertThat(request.getTools()).noneMatch(item -> item.getName().equals("create_plan"));
                    tool(listener, "nested", "create_plan", planJson("STOP"));
                }
                case 3 -> {
                    assertThat(request.getMessages().stream().flatMap(item -> item.getToolResults().stream())
                            .map(item -> item.getErrorCode()).toList()).contains("POLICY_DENIED");
                    text(listener, "step", "递归请求被拒绝");
                }
                case 4 -> text(listener, "final", "计划失败");
                default -> throw new AssertionError("多余模型回合");
            }
        });
        // 递归拒绝后的真实计划结果。
        AgentResult result = service(recursive, new AgentLimits(6, 4, 10000, 100,
                Duration.ofSeconds(5), 3)).run(request());
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED);
        assertThat(result.getError().getCode()).isEqualTo("PLAN_FAILED");
        assertThat(recursive.getCallCount()).isEqualTo(4);

        // 只有创建计划一次工具额度的模型。
        AtomicInteger limitedTurn = new AtomicInteger();
        // 第二次工具调用应在 Java 工具执行前被阻止。
        ScriptedAgentModelGateway limited = new ScriptedAgentModelGateway((request, listener) -> {
            if (limitedTurn.incrementAndGet() == 1) {
                tool(listener, "plan", "create_plan", planJson("STOP"));
            } else {
                tool(listener, "file", "artifact", "{\"name\":\"forbidden.txt\"}");
            }
        });
        // 全局工具额度耗尽后的结果。
        AgentResult exhausted = service(limited, new AgentLimits(6, 1, 10000, 100,
                Duration.ofSeconds(5), 3)).run(request());
        assertThat(exhausted.getStatus()).isEqualTo(AgentResultStatus.LIMIT_EXCEEDED);
        assertThat(limited.getCallCount()).isEqualTo(2);
    }

    /**
     * 验证父运行取消会终止正在等待的计划步骤。
     *
     * @throws Exception 等待步骤或运行结果失败时
     */
    @Test
    void parentCancellationStopsStep() throws Exception {
        // 步骤模型已启动的信号。
        CountDownLatch entered = new CountDownLatch(1);
        // 收到计划后在第一步等待取消的模型。
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (request.getMessages().stream().noneMatch(item -> messageText(item).contains("前序步骤结果"))) {
                tool(listener, "plan", "create_plan", planJson("STOP"));
            } else {
                entered.countDown();
                while (!request.getExecutionContext().getCancellation().isCancelled()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
        // 正在执行的父运行。
        AgentRun run = service(gateway, new AgentLimits(6, 4, 10000, 100,
                Duration.ofSeconds(5), 3)).prepare(request());
        run.execute();
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        run.cancel();
        assertThat(run.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.CANCELLED);
        assertThat(gateway.getCallCount()).isEqualTo(2);
    }

    /**
     * 创建固定授权的测试服务。
     *
     * @param gateway 本地脚本模型
     * @param limits 整次运行预算
     * @return Agent 服务
     */
    private static DefaultAgentService service(ScriptedAgentModelGateway gateway, AgentLimits limits) {
        // 公共默认提示词仓库。
        PromptTemplateRegistry prompts = new PromptTemplateRegistry(new DefaultResourceLoader(),
                Map.of(), Map.of());
        // 计划和产物工具的可信定义。
        AgentDefinition definition = new AgentDefinition("app", "planner", "计划助手", "顺序执行计划",
                "scripted", null, List.of("create_plan", "artifact"), List.of(), limits);
        return new DefaultAgentService(gateway, prompts, List.of(definition),
                new ToolRegistry(List.of(new CreatePlanTool(), new PlanExecutionTestArtifactTool())), List.of(),
                ForkJoinPool.commonPool());
    }

    /**
     * 构造测试请求。
     *
     * @return 计划请求
     */
    private static AgentRequest request() {
        return new AgentRequest("app", "planner", null, "s13", "生成源文件并编译");
    }

    /**
     * 生成两步计划参数。
     *
     * @param policy 失败策略
     * @return JSON 参数
     */
    private static String planJson(String policy) {
        return "{\"name\":\"示例\",\"description\":\"生成与编译\",\"steps\":[\"生成源文件\",\"编译源文件\"],"
                + "\"failurePolicy\":\"" + policy + "\"}";
    }

    /**
     * 返回一次完整工具调用。
     *
     * @param listener 模型监听器
     * @param id 调用标识
     * @param name 工具名称
     * @param json 参数 JSON
     */
    private static void tool(ModelEventListener listener, String id, String name, String json) {
        // 完整助手消息。
        Message assistant = new Message("assistant-" + id, Role.ASSISTANT, List.of(),
                List.of(new ToolCall(id, name, json)), List.of(), Map.of());
        listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.TOOL_CALLS)));
        listener.onComplete();
    }

    /**
     * 返回一次完整文本回答。
     *
     * @param listener 模型监听器
     * @param id 消息标识
     * @param answer 回答文本
     */
    private static void text(ModelEventListener listener, String id, String answer) {
        // 完整助手消息。
        Message assistant = new Message(id, Role.ASSISTANT, List.of(new TextContentBlock(answer)),
                List.of(), List.of(), Map.of());
        listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.STOP)));
        listener.onComplete();
    }

    /**
     * 返回文本消息内容；非文本消息为空字符串。
     *
     * @param message 模型消息
     * @return 文本内容
     */
    private static String messageText(Message message) {
        return message.getContentBlocks().isEmpty() ? ""
                : ((TextContentBlock) message.getContentBlocks().get(0)).getText();
    }


}
