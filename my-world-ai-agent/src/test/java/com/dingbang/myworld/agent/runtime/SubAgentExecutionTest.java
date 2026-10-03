package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentLimits;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.orchestration.CreateSubAgentTool;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;
import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelRequest;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用脚本模型验证子 Agent 的隔离、授权、预算、失败和取消。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class SubAgentExecutionTest {
    /**
     * 验证父子历史隔离、显式上下文、工具子集和用量汇总。
     */
    @Test
    void delegatesReadOnlyReviewAndKeepsHistoriesSeparate() {
        /** 父级模型回合序号。 */
        AtomicInteger parentTurns = new AtomicInteger();
        /** 子级模型回合序号。 */
        AtomicInteger childTurns = new AtomicInteger();
        /** 固定父子交替响应的脚本模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (isChild(request)) {
                if (childTurns.incrementAndGet() == 1) {
                    tool(listener, "read", "read_probe", "{\"path\":\"A.java\"}", 3);
                } else {
                    text(listener, "review", "审查通过", 4);
                }
            } else if (parentTurns.incrementAndGet() == 1) {
                tool(listener, "delegate", "create_sub_agent", delegateJson("read_probe"), 2);
            } else {
                text(listener, "parent-final", "已收到审查结论", 5);
            }
        });
        /** 授权只读工具和委派能力的服务。 */
        DefaultAgentService service = service(gateway, limits(8, 6, 2), true);
        /** 父级最终结果。 */
        AgentResult result = service.run(request());
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(result.getUsage().getTotalTokens()).isEqualTo(14);
        assertThat(gateway.getCallCount()).isEqualTo(4);
        /** 子级第一次模型请求。 */
        ModelRequest childRequest = gateway.getRequests().stream().filter(SubAgentExecutionTest::isChild)
                .findFirst().orElseThrow();
        assertThat(childRequest.getTools()).extracting(item -> item.getName()).containsExactly("read_probe");
        assertThat(childRequest.getMessages()).hasSize(2);
        assertThat(childRequest.getMessages().stream().map(SubAgentExecutionTest::messageText).toList())
                .anyMatch(text -> text.contains("目标 A.java"))
                .noneMatch(text -> text.contains("PRIVATE_PARENT"));
        /** 父级提交的完整历史。 */
        List<Message> parentHistory = service.getSession("app", "app", "parent", result.getSessionId())
                .getMessages();
        assertThat(parentHistory).extracting(Message::getRole)
                .containsExactly(Role.USER, Role.ASSISTANT, Role.TOOL, Role.ASSISTANT);
        assertThat(parentHistory.stream().flatMap(message -> message.getToolCalls().stream())
                .map(ToolCall::getName).toList()).containsExactly("create_sub_agent");
        /** 与父级委派调用配对的真实工具结果。 */
        ToolResult delegatedResult = parentHistory.get(2).getToolResults().get(0);
        assertThat(delegatedResult.getCallId()).isEqualTo("delegate");
        assertThat(delegatedResult.getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(delegatedResult.getContent()).contains("审查通过");
        /** 工具结果中可追踪的子运行标识。 */
        String childRunId = delegatedResult.getContent().split("runId=")[1].split(" ")[0];
        /** 工具结果中可追踪的子会话标识。 */
        String childSessionId = delegatedResult.getContent().split("sessionId=")[1].split("\\n")[0];
        /** 独立子会话的完整历史。 */
        List<Message> childHistory = service.getSession("app", "app", "parent:child:" + childRunId,
                childSessionId).getMessages();
        assertThat(childHistory).extracting(Message::getRole)
                .containsExactly(Role.USER, Role.ASSISTANT, Role.TOOL, Role.ASSISTANT);
        assertThat(childHistory.stream().flatMap(message -> message.getToolCalls().stream())
                .map(ToolCall::getName).toList()).containsExactly("read_probe");
        System.out.println("S14 scripted trace: parent delegate -> child read_probe -> child review"
                + " -> parent tool result -> parent final; usage=14");
    }

    /**
     * 验证模型无法给子级增加父级未授权工具，零层深度也被拒绝。
     */
    @Test
    void rejectsPrivilegeExpansionAndDepthOverflow() {
        /** 两种越权申请的工具名称。 */
        for (String requestedTool : List.of("danger_probe", "read_probe")) {
            /** 当前场景是否为深度拒绝。 */
            boolean depthCase = requestedTool.equals("read_probe");
            /** 父模型回合序号。 */
            AtomicInteger turns = new AtomicInteger();
            /** 委派后只检查配对错误的脚本模型。 */
            ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
                if (turns.incrementAndGet() == 1) {
                    tool(listener, "delegate", "create_sub_agent", delegateJson(requestedTool), 0);
                } else {
                    text(listener, "final", "委派被拒绝", 0);
                }
            });
            /** 仅父级拥有只读工具的服务。 */
            DefaultAgentService service = service(gateway, limits(4, 4, depthCase ? 0 : 2), true);
            /** 拒绝后父运行仍能解释原因。 */
            AgentResult result = service.run(request());
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
            assertThat(gateway.getCallCount()).isEqualTo(2);
            assertThat(service.getSession("app", "app", "parent", result.getSessionId()).getMessages().get(2)
                    .getToolResults().get(0).getErrorCode()).isEqualTo("TOOL_VALIDATION_ERROR");
        }
    }

    /**
     * 验证子级模型失败作为可解释的工具错误回到父模型。
     */
    @Test
    void returnsChildFailureToParent() {
        /** 父模型回合序号。 */
        AtomicInteger parentTurns = new AtomicInteger();
        /** 子模型固定失败的脚本模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (isChild(request)) {
                listener.onError(new IllegalStateException("子级检查失败"));
            } else if (parentTurns.incrementAndGet() == 1) {
                tool(listener, "delegate", "create_sub_agent", delegateJson("read_probe"), 0);
            } else {
                text(listener, "final", "子任务失败，需人工检查", 0);
            }
        });
        /** 可信服务。 */
        DefaultAgentService service = service(gateway, limits(6, 4, 2), true);
        /** 父级解释后的结果。 */
        AgentResult result = service.run(request());
        /** 子级错误的配对工具结果。 */
        ToolResult childError = service.getSession("app", "app", "parent", result.getSessionId())
                .getMessages().get(2).getToolResults().get(0);
        assertThat(childError.getStatus()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(childError.getErrorCode()).isEqualTo("SUB_AGENT_FAILED");
        assertThat(childError.getContent()).contains("子级检查失败");
        assertThat(result.getFinalText()).contains("需人工检查");
    }

    /**
     * 验证子级再次委派会被程序深度限制拒绝，拒绝结果仍与调用配对。
     */
    @Test
    void nestedDelegationCannotExceedConfiguredDepth() {
        /** 父级模型回合序号。 */
        AtomicInteger parentTurns = new AtomicInteger();
        /** 子级模型回合序号。 */
        AtomicInteger childTurns = new AtomicInteger();
        /** 子级伪造下一层委派的脚本模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (isChild(request)) {
                if (childTurns.incrementAndGet() == 1) {
                    tool(listener, "nested", "create_sub_agent", delegateJson("read_probe"), 0);
                } else {
                    text(listener, "child-final", "嵌套委派被拒绝", 0);
                }
            } else if (parentTurns.incrementAndGet() == 1) {
                tool(listener, "delegate", "create_sub_agent", delegateJson("create_sub_agent"), 0);
            } else {
                text(listener, "parent-final", "子级已报告深度限制", 0);
            }
        });
        /** 根运行只允许一层子 Agent。 */
        AgentResult result = service(gateway, limits(6, 5, 1), true).run(request());
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getCallCount()).isEqualTo(4);
        /** 子级第二轮收到的嵌套委派错误。 */
        ModelRequest childSecond = gateway.getRequests().stream().filter(SubAgentExecutionTest::isChild)
                .skip(1).findFirst().orElseThrow();
        assertThat(childSecond.getMessages().stream().flatMap(message -> message.getToolResults().stream())
                .map(ToolResult::getErrorCode).toList()).contains("TOOL_VALIDATION_ERROR");
    }

    /**
     * 验证父级已消耗的工具额度不能由子级重新获得。
     */
    @Test
    void childCannotResetSharedToolBudget() {
        /** 父级模型回合序号。 */
        AtomicInteger parentTurns = new AtomicInteger();
        /** 子级尝试调用只读工具的脚本模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (isChild(request)) {
                tool(listener, "read", "read_probe", "{\"path\":\"A.java\"}", 0);
            } else if (parentTurns.incrementAndGet() == 1) {
                tool(listener, "delegate", "create_sub_agent", delegateJson("read_probe"), 0);
            } else {
                text(listener, "parent-final", "子级工具预算不足", 0);
            }
        });
        /** 根预算只允许 create_sub_agent 这一次工具调用。 */
        DefaultAgentService service = service(gateway, limits(4, 1, 2), true);
        /** 父级仍能接收并解释子级超限。 */
        AgentResult result = service.run(request());
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(gateway.getCallCount()).isEqualTo(3);
        assertThat(service.getSession("app", "app", "parent", result.getSessionId()).getMessages().get(2)
                .getToolResults().get(0).getContent()).contains("LIMIT_EXCEEDED");
    }

    /**
     * 验证单工作线程执行器不会因父级等待子级而死锁。
     *
     * @throws Exception 等待父级结果失败时
     */
    @Test
    void singleWorkerExecutorCanCompleteChildAndParent() throws Exception {
        /** 只有一个工作线程的执行器。 */
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            /** 父级模型回合序号。 */
            AtomicInteger parentTurns = new AtomicInteger();
            /** 单回合结束的子任务模型。 */
            ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
                if (isChild(request)) {
                    text(listener, "child-final", "检查完成", 0);
                } else if (parentTurns.incrementAndGet() == 1) {
                    tool(listener, "delegate", "create_sub_agent", delegateJson("read_probe"), 0);
                } else {
                    text(listener, "parent-final", "已收到结果", 0);
                }
            });
            /** 在单线程执行器上的父运行。 */
            AgentRun run = service(gateway, limits(3, 3, 2), true, executor).prepare(request());
            run.execute();
            assertThat(run.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS).getStatus())
                    .isEqualTo(AgentResultStatus.COMPLETED);
            assertThat(gateway.getCallCount()).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证父取消会结束正在等待模型的子运行。
     *
     * @throws Exception 等待子模型和父结果失败时
     */
    @Test
    void parentCancellationStopsChild() throws Exception {
        /** 子模型开始执行的信号。 */
        CountDownLatch childEntered = new CountDownLatch(1);
        /** 子模型观察到取消的状态。 */
        AtomicBoolean childCancelled = new AtomicBoolean();
        /** 子模型阻塞直到父级取消的脚本模型。 */
        ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
            if (!isChild(request)) {
                tool(listener, "delegate", "create_sub_agent", delegateJson("read_probe"), 0);
                return;
            }
            childEntered.countDown();
            while (!request.getExecutionContext().getCancellation().isCancelled()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            childCancelled.set(request.getExecutionContext().getCancellation().isCancelled());
        });
        /** 正在执行的父运行。 */
        AgentRun run = service(gateway, limits(6, 4, 2), true).prepare(request());
        run.execute();
        assertThat(childEntered.await(3, TimeUnit.SECONDS)).isTrue();
        run.cancel();
        assertThat(run.getResult().toCompletableFuture().get(3, TimeUnit.SECONDS).getStatus())
                .isEqualTo(AgentResultStatus.CANCELLED);
        /** 子级协作取消可能在父结果之后才被工作线程观察到。 */
        for (int attempt = 0; attempt < 100 && !childCancelled.get(); attempt++) {
            Thread.sleep(10);
        }
        assertThat(childCancelled.get()).isTrue();
        assertThat(gateway.getCallCount()).isEqualTo(2);
    }

    /**
     * 返回授权固定的测试服务。
     *
     * @param gateway 本地脚本模型
     * @param limits 根运行预算
     * @param allowRead 是否授权只读工具
     * @return Agent 服务
     */
    private static DefaultAgentService service(ScriptedAgentModelGateway gateway, AgentLimits limits,
                                               boolean allowRead) {
        return service(gateway, limits, allowRead, ForkJoinPool.commonPool());
    }

    /**
     * 返回使用指定执行器的测试服务。
     *
     * @param gateway 本地脚本模型
     * @param limits 根运行预算
     * @param allowRead 是否授权只读工具
     * @param executor 模型与工具执行器
     * @return Agent 服务
     */
    private static DefaultAgentService service(ScriptedAgentModelGateway gateway, AgentLimits limits,
                                               boolean allowRead, Executor executor) {
        /** 公共默认提示词仓库。 */
        PromptTemplateRegistry prompts = new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of());
        /** 父级可信工具权限。 */
        List<String> names = allowRead ? List.of("create_sub_agent", "read_probe")
                : List.of("create_sub_agent");
        /** 固定的父 Agent 定义。 */
        AgentDefinition definition = new AgentDefinition("app", "parent", "父助手", "委派审查",
                "scripted", null, names, List.of(), limits);
        return new DefaultAgentService(gateway, prompts, List.of(definition),
                new ToolRegistry(List.of(new CreateSubAgentTool(), new ReadProbeTool(), new DangerProbeTool())),
                List.of(), executor);
    }

    /**
     * 返回包含父级私有文本的测试请求。
     *
     * @return 父请求
     */
    private static AgentRequest request() {
        return new AgentRequest("app", "parent", null, "s14", "检查 A.java；PRIVATE_PARENT 不得传给子级");
    }

    /**
     * 创建测试预算。
     *
     * @param turns 模型回合数
     * @param tools 工具调用数
     * @param depth 最大子 Agent 深度
     * @return 固定预算
     */
    private static AgentLimits limits(int turns, int tools, int depth) {
        return new AgentLimits(turns, tools, 10000, 100, Duration.ofSeconds(5), 3, depth);
    }

    /**
     * 构造委派参数 JSON。
     *
     * @param toolName 申请的子工具名称
     * @return 参数 JSON
     */
    private static String delegateJson(String toolName) {
        return "{\"name\":\"审查者\",\"description\":\"只读检查\",\"task\":\"检查源码\","
                + "\"context\":\"目标 A.java\",\"toolIds\":[\"" + toolName + "\"]}";
    }

    /**
     * 判断模型请求是否属于独立子会话。
     *
     * @param request 模型请求
     * @return 属于子会话时为 true
     */
    private static boolean isChild(ModelRequest request) {
        return messageText(request.getMessages().get(0)).contains("独立会话");
    }

    /**
     * 返回文本消息内容。
     *
     * @param message 模型消息
     * @return 文本或空字符串
     */
    private static String messageText(Message message) {
        return message.getContentBlocks().isEmpty() ? ""
                : ((TextContentBlock) message.getContentBlocks().get(0)).getText();
    }

    /**
     * 返回带用量的完整工具调用。
     *
     * @param listener 模型监听器
     * @param id 调用标识
     * @param name 工具名称
     * @param json 参数 JSON
     * @param tokens 本回合 token 总数
     */
    private static void tool(ModelEventListener listener, String id, String name, String json, long tokens) {
        /** 模型完成的工具调用消息。 */
        Message assistant = new Message("assistant-" + id, Role.ASSISTANT, List.of(),
                List.of(new ToolCall(id, name, json)), List.of(), Map.of());
        listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.TOOL_CALLS,
                new ModelTokenUsage(0, tokens, tokens, null))));
        listener.onComplete();
    }

    /**
     * 返回带用量的完整文本回答。
     *
     * @param listener 模型监听器
     * @param id 消息标识
     * @param answer 文本回答
     * @param tokens 本回合 token 总数
     */
    private static void text(ModelEventListener listener, String id, String answer, long tokens) {
        /** 模型完成的文本消息。 */
        Message assistant = new Message(id, Role.ASSISTANT, List.of(new TextContentBlock(answer)),
                List.of(), List.of(), Map.of());
        listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.STOP,
                new ModelTokenUsage(0, tokens, tokens, null))));
        listener.onComplete();
    }

    /**
     * 只读检查工具的参数。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public static final class ReadParameters {
        /** 待读文件路径。 */
        @ToolParam(description = "待读文件路径")
        public String path;
    }

    /**
     * 返回固定检查结果的只读工具。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    @ToolInfo(name = "read_probe", description = "只读检查文件")
    public static final class ReadProbeTool implements Tool<ReadParameters> {
        /**
         * 返回工具参数类型。
         *
         * @return 参数类
         */
        @Override
        public Class<ReadParameters> parameterType() {
            return ReadParameters.class;
        }

        /**
         * 返回固定的只读检查结果。
         *
         * @param parameters 文件参数
         * @param context 可信运行上下文
         * @return 检查文本
         */
        @Override
        public ToolExecutionResult execute(ReadParameters parameters, ToolExecutionContext context) {
            context.checkActive();
            return ToolExecutionResult.text(parameters.path + " 源文件正常");
        }
    }

    /**
     * 表示父运行未授权的危险工具。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    @ToolInfo(name = "danger_probe", description = "测试未授权能力")
    public static final class DangerProbeTool implements Tool<ReadParameters> {
        /**
         * 返回工具参数类型。
         *
         * @return 参数类
         */
        @Override
        public Class<ReadParameters> parameterType() {
            return ReadParameters.class;
        }

        /**
         * 若运行会到达此处则返回标记。
         *
         * @param parameters 文件参数
         * @param context 可信运行上下文
         * @return 标记文本
         */
        @Override
        public ToolExecutionResult execute(ReadParameters parameters, ToolExecutionContext context) {
            return ToolExecutionResult.text("UNAUTHORIZED");
        }
    }
}
