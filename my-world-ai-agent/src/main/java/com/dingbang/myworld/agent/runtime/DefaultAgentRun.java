package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentError;
import com.dingbang.myworld.agent.api.AgentLimits;
import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventListener;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.prompt.PromptTemplateSnapshot;
import com.dingbang.myworld.agent.orchestration.CreatePlanTool;
import com.dingbang.myworld.agent.orchestration.CreateSubAgentTool;
import com.dingbang.myworld.agent.orchestration.Plan;
import com.dingbang.myworld.agent.orchestration.PlanEvent;
import com.dingbang.myworld.agent.orchestration.PlanRunner;
import com.dingbang.myworld.agent.orchestration.PlanStep;
import com.dingbang.myworld.agent.orchestration.PlanStepStatus;
import com.dingbang.myworld.agent.orchestration.PlanStepOutcome;
import com.dingbang.myworld.agent.orchestration.SubAgentRunner;
import com.dingbang.myworld.agent.session.Session;
import com.dingbang.myworld.agent.session.SessionRepository;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.agent.tool.ToolDescriptor;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionEvent;
import com.dingbang.myworld.agent.tool.ToolExecutor;
import com.dingbang.myworld.agent.tool.ToolExecutionPhase;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiframework.api.CancellationToken;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;
import com.dingbang.myworld.aiframework.api.ModelExecutionContext;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelGatewayException;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.ModelTokenUsage;
import com.dingbang.myworld.aiframework.api.ModelToolDefinition;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.ReasoningDelta;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.api.event.UsageReported;
import com.dingbang.myworld.aiframework.api.event.ValidatingModelEventListener;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 将模型回合、可信工具和全局执行预算组成唯一 Agent 循环。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
final class DefaultAgentRun implements AgentRun {
    /** 本次运行标识。 */
    private final String runId;
    /** 所属会话标识。 */
    private final String sessionId;
    /** 可信 Agent 定义。 */
    private final AgentDefinition definition;
    /** 用户请求快照。 */
    private final AgentRequest request;
    /** 已渲染的系统消息。 */
    private final Message systemMessage;
    /** 固定的模板版本。 */
    private final PromptTemplateSnapshot template;
    /** 所属进程内会话。 */
    private final Session session;
    /** 单次模型入口。 */
    private final ModelGateway gateway;
    /** 运行级授权工具执行器。 */
    private final ToolExecutor toolExecutor;
    /** 当前运行的可信工具快照。 */
    private final ToolRegistry authorizedTools;
    /** 固定的计划步骤模板；未授权计划时为 null。 */
    private final PromptTemplateSnapshot planStepTemplate;
    /** 固定的子 Agent 模板；未授权子 Agent 时为 null。 */
    private final PromptTemplateSnapshot subAgentTemplate;
    /** 创建独立子会话所用的可信仓库。 */
    private final SessionRepository sessions;
    /** 当前运行的子 Agent 嵌套层数。 */
    private final int depth;
    /** 子运行继承的父级截止时间；根运行为 null。 */
    private final Instant inheritedDeadline;
    /** 模型可见的可信工具说明。 */
    private final List<ModelToolDefinition> modelTools;
    /** 执行模型和工具的专用执行器。 */
    private final Executor executor;
    /** 独立事件消费者的有限发布器。 */
    private final AgentEventPublisher eventPublisher;
    /** 所有调用方共享的唯一结果。 */
    private final CompletableFuture<AgentResult> completion = new CompletableFuture<>();
    /** 协议及工具共享的取消令牌。 */
    private final CancellationToken cancellation = new CancellationToken();
    /** 保护状态、消息、用量及事件序列的锁。 */
    private final Object stateLock = new Object();
    /** execute 是否已经启动过。 */
    private boolean started;
    /** 是否已取得唯一终态。 */
    private boolean terminal;
    /** 是否持有会话运行权。 */
    private boolean ownsSession;
    /** 当前运行的事件序号。 */
    private long eventSequence;
    /** 已完成模型回合的用量。 */
    private ModelTokenUsage usageTotal;
    /** 已计入本运行及子树的模型调用用量，按调用标识去重。 */
    private final Map<String, ModelTokenUsage> usageByModelCallId = new LinkedHashMap<>();
    /** 已接纳的模型和工具输出字符数。 */
    private long outputCharacters;
    /** 已准备执行的工具调用次数。 */
    private int toolCalls;
    /** 本运行及子树已经使用的模型回合数。 */
    private int modelTurnsUsed;
    /** 正在构造的本轮交换。 */
    private List<Message> exchange;
    /** 模型下一回合需要的完整上下文。 */
    private List<Message> messages;
    /** 本轮读取的历史版本。 */
    private long sessionVersion;
    /** 全局截止时间。 */
    private Instant deadline;
    /** 可在任意终态撤销的超时任务。 */
    private ScheduledFuture<?> timeoutTask;
    /** 当前计划的独立步骤状态；没有执行计划时为 null。 */
    private PlanRunner planRunner;
    /** 计划调用所在的父级消息列表。 */
    private List<Message> parentMessages;
    /** 计划调用所在的父级交换。 */
    private List<Message> parentExchange;
    /** 创建计划之前的有效消息。 */
    private List<Message> planBaseMessages;
    /** 已完成步骤的有效模型对话。 */
    private final List<Message> planTranscript = new ArrayList<>();
    /** 当前步骤对话在消息中的起点。 */
    private int stepTranscriptStart;
    /** 当前计划对应的模型工具调用。 */
    private ToolCall planCall;
    /** 当前步骤是否发生未被证明已恢复的工具错误。 */
    private boolean stepHadToolError;
    /** 本次运行是否有失败计划。 */
    private boolean planFailed;
    /** 失败计划的真实结果。 */
    private String failedPlanSummary;

    /**
     * 保存准备阶段固定的运行输入。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param definition Agent 定义
     * @param request 用户请求
     * @param systemMessage 已渲染系统消息
     * @param template 模板快照
     * @param session 进程内会话
     * @param gateway 单次模型入口
     * @param tools 运行级工具授权快照
     * @param planStepTemplate 固定的计划步骤模板，未授权计划时为 null
     * @param subAgentTemplate 固定的子 Agent 模板，未授权委派时为 null
     * @param sessions 创建独立子会话的仓库
     * @param executor 执行模型和工具的后台执行器
     * @param depth 当前子 Agent 层数
     * @param inheritedDeadline 父级截止时间，根运行为 null
     */
    DefaultAgentRun(String runId, String sessionId, AgentDefinition definition, AgentRequest request,
                    Message systemMessage, PromptTemplateSnapshot template,
                    Session session, ModelGateway gateway, ToolRegistry tools,
                    PromptTemplateSnapshot planStepTemplate, PromptTemplateSnapshot subAgentTemplate,
                    SessionRepository sessions, Executor executor, int depth, Instant inheritedDeadline) {
        this.runId = Objects.requireNonNull(runId, "运行标识不能为 null");
        this.sessionId = Objects.requireNonNull(sessionId, "会话标识不能为 null");
        this.definition = Objects.requireNonNull(definition, "Agent 定义不能为 null");
        this.request = Objects.requireNonNull(request, "用户请求不能为 null");
        this.systemMessage = Objects.requireNonNull(systemMessage, "系统消息不能为 null");
        this.template = Objects.requireNonNull(template, "模板快照不能为 null");
        this.session = Objects.requireNonNull(session, "会话不能为 null");
        this.gateway = Objects.requireNonNull(gateway, "模型入口不能为 null");
        this.authorizedTools = Objects.requireNonNull(tools, "工具注册表不能为 null");
        this.toolExecutor = new ToolExecutor(tools);
        this.planStepTemplate = planStepTemplate;
        this.subAgentTemplate = subAgentTemplate;
        this.sessions = Objects.requireNonNull(sessions, "会话仓库不能为 null");
        this.depth = depth;
        this.inheritedDeadline = inheritedDeadline;
        /** 本轮模型可见的工具说明。 */
        List<ModelToolDefinition> descriptions = new ArrayList<>();
        for (ToolDescriptor<?> descriptor : tools.getDescriptors()) {
            descriptions.add(new ModelToolDefinition(descriptor.getName(), descriptor.getDescription(),
                    descriptor.getParameterSchema().toString()));
        }
        this.modelTools = Collections.unmodifiableList(descriptions);
        this.executor = Objects.requireNonNull(executor, "模型执行器不能为 null");
        this.eventPublisher = new AgentEventPublisher(definition.getLimits().getEventBufferCapacity());
    }

    /**
     * 返回运行标识。
     *
     * @return 运行标识
     */
    @Override
    public String getRunId() {
        return runId;
    }

    /**
     * 返回会话标识。
     *
     * @return 会话标识
     */
    @Override
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 从头异步订阅当前运行的完整可用事件。
     *
     * @param listener 事件观察者
     */
    @Override
    public void subscribe(AgentEventListener listener) {
        subscribe(listener, 0);
    }

    /**
     * 从指定序号后异步订阅事件；历史缺口由发布器明确报告。
     *
     * @param listener 事件观察者
     * @param afterSequence 已接收的最后序号
     */
    @Override
    public void subscribe(AgentEventListener listener, long afterSequence) {
        eventPublisher.subscribe(listener, afterSequence);
    }

    /**
     * 返回同一次运行的唯一最终结果。
     *
     * @return 共享结果阶段
     */
    @Override
    public CompletionStage<AgentResult> getResult() {
        return completion.thenApply(result -> result);
    }

    /**
     * 启动运行并在可信时限到达时结束 Future。
     *
     * @throws IllegalStateException 重复启动或已取消后启动时
     */
    @Override
    public void execute() {
        synchronized (stateLock) {
            if (started || terminal) {
                throw new IllegalStateException("Agent 运行只能执行一次");
            }
            started = true;
        }
        if (!session.tryStart()) {
            stop(AgentResultStatus.FAILED, AgentEventType.FAILED, "SESSION_BUSY", "会话已有运行正在执行", null);
            return;
        }
        synchronized (stateLock) {
            ownsSession = true;
            if (terminal) {
                session.release();
                ownsSession = false;
                return;
            }
        }
        try {
            /** 当前用户的完整消息。 */
            Message user = textMessage(runId + ":user", Role.USER, request.getUserText());
            synchronized (stateLock) {
                messages = new ArrayList<>();
                messages.add(systemMessage);
                /** 历史和版本的同一快照。 */
                SessionSnapshot snapshot = session.snapshot();
                sessionVersion = snapshot.getVersion();
                messages.addAll(snapshot.getMessages());
                messages.add(user);
                exchange = new ArrayList<>();
                exchange.add(user);
                /** 当前运行的实际截止时间。 */
                Instant ownDeadline = Instant.now().plus(definition.getLimits().getTimeout());
                deadline = inheritedDeadline == null || ownDeadline.isBefore(inheritedDeadline)
                        ? ownDeadline : inheritedDeadline;
                /** 截止时间相对现在的剩余纳秒数。 */
                long remainingNanos = Math.max(1, Duration.between(Instant.now(), deadline).toNanos());
                timeoutTask = AgentExecutors.TIMER.schedule(this::timeout,
                        remainingNanos, TimeUnit.NANOSECONDS);
            }
            scheduleModel(1);
        } catch (RuntimeException exception) {
            fail("PREPARATION_FAILURE", exception);
        }
    }

    /**
     * 幂等取消运行、唤醒等待方并向协议及工具传播信号。
     */
    @Override
    public void cancel() {
        stop(AgentResultStatus.CANCELLED, AgentEventType.CANCELLED, "CANCELLED", "运行已取消", null);
    }

    /**
     * 按运行预算提交下一次模型调用。
     *
     * @param turnNumber 从一开始的模型回合编号
     */
    private void scheduleModel(int turnNumber) {
        /** 固定的本轮请求。 */
        ModelRequest modelRequest;
        synchronized (stateLock) {
            if (terminal) {
                return;
            }
            if (deadlineReached()) {
                timeout();
                return;
            }
            if (turnNumber > definition.getLimits().getMaxModelTurns()) {
                limitExceeded("模型回合数达到限制: " + definition.getLimits().getMaxModelTurns(), null);
                return;
            }
            if (outputCharacters >= definition.getLimits().getMaxOutputCharacters()) {
                limitExceeded("运行输出达到字符上限", null);
                return;
            }
            modelTurnsUsed = Math.max(modelTurnsUsed, turnNumber);
            /** 计划内部从模型可见工具列表中移除递归计划能力。 */
            List<ModelToolDefinition> visibleTools = planRunner == null ? modelTools
                    : modelTools.stream().filter(tool -> !tool.getName().equals("create_plan")).toList();
            modelRequest = new ModelRequest(definition.getModelId(), messages, visibleTools,
                    request.getModelOptions().overlay(session.options()), new ModelExecutionContext(deadline, cancellation,
                    definition.getLimits().getMaxOutputCharacters() - (int) outputCharacters));
        }
        try {
            executor.execute(() -> invokeModel(modelRequest, turnNumber));
        } catch (RuntimeException exception) {
            fail("PREPARATION_FAILURE", exception);
        }
    }

    /**
     * 在可中断的工作线程调用一次模型，且只注册该线程的有效期。
     *
     * @param modelRequest 本轮模型请求
     * @param turnNumber 当前回合编号
     */
    private void invokeModel(ModelRequest modelRequest, int turnNumber) {
        /** 当前阻塞调用所在的线程。 */
        Thread worker = Thread.currentThread();
        /** 正常返回时移除的取消注册。 */
        Runnable unregister = cancellation.onCancel(() -> {
            if (Thread.currentThread() != worker) {
                worker.interrupt();
            }
        });
        try {
            modelRequest.getExecutionContext().checkActive();
            /** 每轮独立校验回调唯一完整结果的监听器。 */
            ModelEventListener listener = new ValidatingModelEventListener(new RoundListener(turnNumber));
            gateway.generate(modelRequest, listener);
        } catch (ExecutionControlException exception) {
            stopForControl(exception);
        } catch (ModelGatewayException exception) {
            handleModelFailure(exception.getCode(), exception, turnNumber);
        } catch (RuntimeException exception) {
            handleModelFailure("MODEL_FAILURE", exception, turnNumber);
        } finally {
            unregister.run();
        }
    }

    /**
     * 处理一次完整模型回合并决定续问或成功收尾。
     *
     * @param turn 完整模型结果
     * @param turnNumber 当前回合编号
     * @param streamedCharacters 本回合已向调用方发送的字符数
     */
    private void completeRound(ModelTurn turn, int turnNumber, long streamedCharacters) {
        try {
            Objects.requireNonNull(turn, "模型未返回完整回合");
            /** 完整助手消息。 */
            Message assistant = turn.getAssistantMessage();
            /** 本回合最终完整内容的字符数。 */
            long completeCharacters = countContent(assistant);
            synchronized (stateLock) {
                if (terminal) {
                    return;
                }
                if (deadlineReached()) {
                    timeout();
                    return;
                }
                if (outputCharacters + Math.max(streamedCharacters, completeCharacters)
                        > definition.getLimits().getMaxOutputCharacters()) {
                    limitExceeded("模型输出达到字符上限", turn.getFinishReason());
                    return;
                }
                outputCharacters += Math.max(streamedCharacters, completeCharacters);
                if (turn.getUsage() != null) {
                    /** 全树唯一的模型调用标识。 */
                    String modelCallId = runId + ":model:" + turnNumber;
                    if (usageByModelCallId.putIfAbsent(modelCallId, turn.getUsage()) == null) {
                        usageTotal = usageTotal == null ? turn.getUsage() : usageTotal.plus(turn.getUsage());
                        emitLocked(AgentEventType.USAGE, null, null, null, turn.getUsage());
                    }
                }
            }
            if (turn.getFinishReason() == ModelFinishReason.TOOL_CALLS) {
                completeTools(assistant, turnNumber);
                return;
            }
            if (!assistant.getToolCalls().isEmpty()) {
                throw new IllegalArgumentException("非工具结束原因却包含工具调用");
            }
            if (turn.getFinishReason() == ModelFinishReason.LENGTH) {
                limitExceeded("模型达到输出长度限制", turn.getFinishReason());
                return;
            }
            if (turn.getFinishReason() != ModelFinishReason.STOP) {
                throw new IllegalArgumentException("模型没有正常完成回答: " + turn.getFinishReason());
            }
            /** 从完整助手消息读取的最终文本。 */
            String finalText = getText(assistant);
            if (planRunner != null) {
                completePlanStep(assistant, finalText, turnNumber);
            } else {
                finishSuccess(assistant, finalText, turn.getFinishReason());
            }
        } catch (ExecutionControlException exception) {
            stopForControl(exception);
        } catch (RuntimeException exception) {
            handleModelFailure("INVALID_MODEL_TURN", exception, turnNumber);
        }
    }

    /**
     * 验证整批调用后顺序执行工具，每次执行前复核取消与时限。
     *
     * @param assistant 完整工具调用消息
     * @param turnNumber 当前回合编号
     */
    private void completeTools(Message assistant, int turnNumber) {
        /** 本回合完整工具调用。 */
        List<ToolCall> calls = assistant.getToolCalls();
        if (calls.isEmpty()) {
            throw new IllegalArgumentException("工具回合缺少调用");
        }
        /** 当前回合已见的工具标识。 */
        Set<String> callIds = new HashSet<>();
        for (ToolCall call : calls) {
            if (!callIds.add(call.getCallId())) {
                throw new IllegalArgumentException("同一助手回合存在重复工具调用标识: " + call.getCallId());
            }
        }
        synchronized (stateLock) {
            if (terminal) {
                return;
            }
            if (turnNumber >= definition.getLimits().getMaxModelTurns()) {
                limitExceeded("模型回合数达到限制: " + definition.getLimits().getMaxModelTurns(),
                        ModelFinishReason.TOOL_CALLS);
                return;
            }
            if ((long) toolCalls + calls.size() > definition.getLimits().getMaxToolCalls()) {
                limitExceeded("工具调用次数达到限制: " + definition.getLimits().getMaxToolCalls(),
                        ModelFinishReason.TOOL_CALLS);
                return;
            }
            toolCalls += calls.size();
            messages.add(assistant);
            if (planRunner == null) {
                exchange.add(assistant);
            }
        }
        if (calls.size() == 1 && "create_plan".equals(calls.get(0).getName()) && planRunner == null
                && planStepTemplate != null) {
            startPlan(calls.get(0), turnNumber);
            return;
        }
        if (calls.size() == 1 && "create_sub_agent".equals(calls.get(0).getName())
                && subAgentTemplate != null) {
            startSubAgent(calls.get(0), turnNumber);
            return;
        }
        for (ToolCall call : calls) {
            synchronized (stateLock) {
                if (terminal) {
                    return;
                }
                if (deadlineReached()) {
                    timeout();
                    return;
                }
            }
            /** 由运行时构造的可信工具上下文。 */
            ToolExecutionContext context = new ToolExecutionContext(runId, sessionId, null, deadline, cancellation);
            /** 与调用标识配对的真实或结构化失败结果。 */
            /** 计划内部的递归调用始终由程序拒绝，即使模型伪造工具名称。 */
            ToolResult result = planRunner != null && "create_plan".equals(call.getName())
                    ? new ToolResult(call.getCallId(), ToolResultStatus.ERROR,
                    "计划步骤不能再创建计划", "POLICY_DENIED", false)
                    : "create_sub_agent".equals(call.getName())
                    ? new ToolResult(call.getCallId(), ToolResultStatus.ERROR,
                    "子 Agent 调用必须单独成批执行", "POLICY_DENIED", false)
                    : toolExecutor.execute(call, context, this::emitToolEvent);
            if (planRunner != null) {
                /** 可为计划声明语义失败的可信工具。 */
                Object authorizedTool = authorizedTools.getAuthorizedTool(call.getName());
                if (result.getStatus() == ToolResultStatus.ERROR
                        || (authorizedTool instanceof PlanStepOutcome
                        && ((PlanStepOutcome) authorizedTool).stepFailed(result))) {
                    synchronized (stateLock) {
                        stepHadToolError = true;
                    }
                }
            }
            synchronized (stateLock) {
                if (terminal) {
                    return;
                }
                if (outputCharacters + result.getContent().length()
                        > definition.getLimits().getMaxOutputCharacters()) {
                    limitExceeded("工具输出达到字符上限", ModelFinishReason.TOOL_CALLS);
                    return;
                }
                outputCharacters += result.getContent().length();
                /** 含单个工具结果的模型消息。 */
                Message toolMessage = new Message(runId + ":tool:" + turnNumber + ":" + call.getCallId(),
                        Role.TOOL, Collections.emptyList(), Collections.emptyList(),
                        Collections.singletonList(result), Collections.emptyMap());
                messages.add(toolMessage);
                if (planRunner == null) {
                    exchange.add(toolMessage);
                }
            }
        }
        scheduleModel(turnNumber + 1);
    }

    /**
     * 校验委派参数，创建独立子会话并在其完成后续接父运行。
     *
     * @param call 已授权的子 Agent 工具调用
     * @param turnNumber 当前父级模型回合数
     */
    private void startSubAgent(ToolCall call, int turnNumber) {
        emitToolEvent(new ToolExecutionEvent(call.getCallId(), call.getName(),
                ToolExecutionPhase.PREPARING, null));
        try {
            if (depth >= definition.getLimits().getMaxSubAgentDepth()) {
                throw new IllegalArgumentException("子 Agent 深度达到限制");
            }
            /** 当前运行已授权的委派描述。 */
            ToolDescriptor<?> descriptor = authorizedTools.getDescriptors().stream()
                    .filter(item -> item.getName().equals("create_sub_agent")
                            && item.getParameterType() == CreateSubAgentTool.Parameters.class)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("子 Agent 工具未获授权"));
            /** 经 Schema 校验的委派参数。 */
            CreateSubAgentTool.Parameters parameters =
                    (CreateSubAgentTool.Parameters) descriptor.parse(call.getArgumentsJson());
            /** 当前运行实际授权的子工具集合。 */
            ToolRegistry childTools = authorizedTools.select(
                    parameters.toolIds == null ? List.of() : parameters.toolIds);
            /** 父级留给子级及最终回答的模型回合数。 */
            int childTurnLimit = definition.getLimits().getMaxModelTurns() - turnNumber - 1;
            /** 已计入父级输出后的剩余字符数。 */
            int remainingOutput = definition.getLimits().getMaxOutputCharacters() - (int) outputCharacters;
            /** 预留给父级结果说明的字符数。 */
            int parentReserve = Math.min(128, Math.max(1, remainingOutput / 4));
            /** 子级可使用的工具调用次数。 */
            int childToolLimit = definition.getLimits().getMaxToolCalls() - toolCalls;
            /** 子级继承的实际剩余时限。 */
            Duration remainingTime = Duration.between(Instant.now(), deadline);
            if (childTurnLimit < 1 || remainingOutput - parentReserve < 1
                    || remainingTime.compareTo(Duration.ofMillis(1)) < 0) {
                throw new IllegalArgumentException("剩余预算不足以启动子 Agent 并回传结果");
            }
            /** 仅由父级可信定义缩小而来的子级预算。 */
            AgentLimits childLimits =
                    new AgentLimits(childTurnLimit, childToolLimit,
                            remainingOutput - parentReserve,
                            definition.getLimits().getEventBufferCapacity(), remainingTime,
                            definition.getLimits().getMaxPlanSteps(),
                            definition.getLimits().getMaxSubAgentDepth());
            /** 与父会话不同的子运行标识。 */
            String childRunId = UUID.randomUUID().toString();
            /** 与父会话不同的子会话标识。 */
            String childSessionId = UUID.randomUUID().toString();
            /** 子定义的内部可信身份。 */
            String childAgentId = definition.getAgentId() + ":child:" + childRunId;
            /** 当前委派专用的子 Agent 定义。 */
            AgentDefinition childDefinition = new AgentDefinition(definition.getAppId(), childAgentId,
                    parameters.name, parameters.description, definition.getModelId(),
                    subAgentTemplate.getTemplateId(),
                    parameters.toolIds == null ? List.of() : parameters.toolIds, List.of(), childLimits);
            /** 父级本轮有效模型选项的不可变快照。 */
            ModelOptions effectiveOptions = request.getModelOptions().overlay(session.options());
            /** 子级只接收委派任务和显式上下文。 */
            AgentRequest childRequest = new AgentRequest(request.getOwnerId(), request.getAppId(),
                    childAgentId, null, request.getRequestId() + ":child:" + call.getCallId(),
                    parameters.task, effectiveOptions);
            /** 子运行专用系统消息。 */
            Message childSystem = subAgentTemplate.toSystemMessage(childRunId + ":system",
                    Map.of("agentName", parameters.name,
                            "agentDescription", parameters.description,
                            "delegatedTask", parameters.task,
                            "delegatedContext", parameters.context == null ? "无" : parameters.context));
            /** 独立会话不会读写父历史。 */
            Session childSession = sessions.create(childSessionId, request.getOwnerId(),
                    request.getAppId(), childAgentId, ModelOptions.empty());
            /** 使用同一运行时和服务，但状态及授权独立的子运行。 */
            DefaultAgentRun child = new DefaultAgentRun(childRunId, childSessionId,
                    childDefinition, childRequest, childSystem, subAgentTemplate, childSession,
                    gateway, childTools,
                    childTools.getAuthorizedTool("create_plan") == null ? null : planStepTemplate,
                    childTools.getAuthorizedTool("create_sub_agent") == null ? null : subAgentTemplate,
                    sessions, executor, depth + 1, deadline);
            emitToolEvent(new ToolExecutionEvent(call.getCallId(), call.getName(),
                    ToolExecutionPhase.CALLING, null));
            /** 子级在父取消时终止，完成回调再续接父级模型。 */
            new SubAgentRunner(child, cancellation).start((childResult, error) -> {
                try {
                    finishSubAgent(call, turnNumber, parameters.name, child, childResult, error);
                } catch (RuntimeException exception) {
                    fail("SUB_AGENT_CALLBACK_FAILURE", exception);
                }
            });
            return;
        } catch (IllegalArgumentException exception) {
            finishSubAgentTool(call, turnNumber, 0, new ToolResult(call.getCallId(),
                    ToolResultStatus.ERROR, exception.getMessage(), "TOOL_VALIDATION_ERROR", false));
        } catch (RuntimeException exception) {
            finishSubAgentTool(call, turnNumber, 0, new ToolResult(call.getCallId(), ToolResultStatus.ERROR,
                    exception.getMessage() == null ? exception.getClass().getSimpleName()
                            : exception.getMessage(), "SUB_AGENT_FAILED", false));
        }
    }

    /**
     * 在子运行取得终态后，将其唯一结果和已使用预算回传父级。
     *
     * @param call 父级委派调用
     * @param turnNumber 父级当前模型回合数
     * @param name 子 Agent 名称
     * @param child 独立子运行
     * @param childResult 子运行结果
     * @param error 子运行完成异常，可为 null
     */
    private void finishSubAgent(ToolCall call, int turnNumber, String name, DefaultAgentRun child,
                                AgentResult childResult, Throwable error) {
        synchronized (stateLock) {
            if (terminal) {
                return;
            }
        }
        mergeChildAccounting(child);
        /** 子结果仅作为当前工具调用的配对内容返回。 */
        String content = error != null ? "子 Agent 执行异常\nrunId=" + child.getRunId()
                + " sessionId=" + child.getSessionId() + "\n" + error.getMessage()
                : "子 Agent " + name + " [" + childResult.getStatus() + "]\n"
                + "runId=" + child.getRunId() + " sessionId=" + child.getSessionId() + "\n"
                + (childResult.getStatus() == AgentResultStatus.COMPLETED
                ? childResult.getFinalText()
                : childResult.getError().getCode() + ": " + childResult.getError().getMessage());
        /** 失败或成功均与父调用标识配对。 */
        ToolResult toolResult = new ToolResult(call.getCallId(),
                error == null && childResult.getStatus() == AgentResultStatus.COMPLETED
                        ? ToolResultStatus.SUCCESS : ToolResultStatus.ERROR,
                content, error == null && childResult.getStatus() == AgentResultStatus.COMPLETED
                ? null : "SUB_AGENT_FAILED", false);
        finishSubAgentTool(call, turnNumber, child.modelTurnsUsed, toolResult);
    }

    /**
     * 发布委派工具终态并用配对结果继续父级模型回合。
     *
     * @param call 父级委派调用
     * @param turnNumber 父级当前模型回合数
     * @param childTurns 子树消耗的模型回合数
     * @param toolResult 真实或结构化错误结果
     */
    private void finishSubAgentTool(ToolCall call, int turnNumber, int childTurns, ToolResult toolResult) {
        emitToolEvent(new ToolExecutionEvent(call.getCallId(), call.getName(),
                toolResult.getStatus() == ToolResultStatus.SUCCESS
                        ? ToolExecutionPhase.COMPLETED : ToolExecutionPhase.FAILED, toolResult));
        if (planRunner != null && toolResult.getStatus() == ToolResultStatus.ERROR) {
            synchronized (stateLock) {
                stepHadToolError = true;
            }
        }
        appendOrchestrationToolResult(toolResult);
        scheduleModel(turnNumber + childTurns + 1);
    }

    /**
     * 将子树使用的回合、工具、输出和按 modelCallId 去重的用量计入父级。
     *
     * @param child 已结束的独立子运行
     */
    private void mergeChildAccounting(DefaultAgentRun child) {
        synchronized (child.stateLock) {
            synchronized (stateLock) {
                modelTurnsUsed += child.modelTurnsUsed;
                toolCalls += child.toolCalls;
                outputCharacters += child.outputCharacters;
                /** 子树中每次模型调用的最终用量。 */
                for (Map.Entry<String, ModelTokenUsage> entry : child.usageByModelCallId.entrySet()) {
                    if (usageByModelCallId.putIfAbsent(entry.getKey(), entry.getValue()) == null) {
                        usageTotal = usageTotal == null ? entry.getValue() : usageTotal.plus(entry.getValue());
                    }
                }
            }
        }
    }

    /**
     * 校验计划调用并开始第一步；普通参数错误作为工具结果回传模型。
     *
     * @param call 已授权的计划工具调用
     * @param turnNumber 当前全局模型回合数
     */
    private void startPlan(ToolCall call, int turnNumber) {
        emitToolEvent(new ToolExecutionEvent(call.getCallId(), call.getName(),
                ToolExecutionPhase.PREPARING, null));
        /** 经校验的不可变计划。 */
        Plan plan;
        try {
            /** 从可信工具快照读取的计划描述。 */
            ToolDescriptor<?> descriptor = authorizedTools.getDescriptors().stream()
                    .filter(item -> item.getName().equals("create_plan")
                            && item.getParameterType() == CreatePlanTool.Parameters.class)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("计划工具未获授权"));
            /** 经 Schema 严格校验的模型参数。 */
            CreatePlanTool.Parameters parameters = (CreatePlanTool.Parameters) descriptor.parse(call.getArgumentsJson());
            /** 已校验并受可信上限约束的计划。 */
            plan = new Plan(parameters.name, parameters.description, parameters.steps,
                    parameters.failurePolicy == null ? Plan.FailurePolicy.STOP : parameters.failurePolicy,
                    definition.getLimits().getMaxPlanSteps());
        } catch (IllegalArgumentException exception) {
            /** 与失败调用配对的校验结果。 */
            ToolResult result = new ToolResult(call.getCallId(), ToolResultStatus.ERROR,
                    exception.getMessage(), "TOOL_VALIDATION_ERROR", false);
            emitToolEvent(new ToolExecutionEvent(call.getCallId(), call.getName(),
                    ToolExecutionPhase.FAILED, result));
            appendOrchestrationToolResult(result);
            scheduleModel(turnNumber + 1);
            return;
        }
        emitToolEvent(new ToolExecutionEvent(call.getCallId(), call.getName(),
                ToolExecutionPhase.CALLING, null));
        synchronized (stateLock) {
            if (terminal) return;
            planRunner = new PlanRunner(plan);
            planCall = call;
            parentMessages = messages;
            parentExchange = exchange;
            planBaseMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
            planTranscript.clear();
            emitPlanLocked(AgentEventType.PLAN_CREATED, 0, null, plan.getDescription());
        }
        advancePlan(turnNumber);
    }

    /**
     * 准备下一步骤或向父级回传完整计划结果。
     *
     * @param turnNumber 上一个模型回合数
     */
    private void advancePlan(int turnNumber) {
        /** 计划中的下一步骤。 */
        PlanStep step = planRunner.startNext();
        if (step == null) {
            finishPlan(turnNumber);
            return;
        }
        /** 模板中只放当前步骤的受控执行说明。 */
        Message stepSystem = planStepTemplate.toSystemMessage(runId + ":plan:system:" + step.getNumber(),
                Map.of("planGoal", planRunner.getPlan().getDescription(),
                        "stepNumber", Integer.toString(step.getNumber()),
                        "stepCount", Integer.toString(planRunner.getPlan().getSteps().size()),
                        "stepDescription", step.getDescription()));
        synchronized (stateLock) {
            if (terminal) return;
            messages = new ArrayList<>();
            messages.add(planBaseMessages.get(0));
            messages.add(stepSystem);
            messages.addAll(planBaseMessages.subList(1, planBaseMessages.size()));
            messages.addAll(planTranscript);
            stepTranscriptStart = messages.size();
            messages.add(textMessage(runId + ":plan:user:" + step.getNumber(), Role.USER,
                    "计划目标：" + planRunner.getPlan().getDescription() + "\n步骤 " + step.getNumber()
                            + "/" + planRunner.getPlan().getSteps().size() + "：" + step.getDescription()
                            + "\n前序步骤结果：\n"
                            + planRunner.priorResults()));
            stepHadToolError = false;
            emitPlanLocked(AgentEventType.PLAN_STEP_STARTED, step.getNumber(),
                    PlanStepStatus.RUNNING, step.getDescription());
        }
        scheduleModel(turnNumber + 1);
    }

    /**
     * 记录完整的步骤回答和工具轨迹，再推进计划。
     *
     * @param assistant 本步骤最终模型消息
     * @param finalText 本步骤最终文本
     * @param turnNumber 当前全局模型回合数
     */
    private void completePlanStep(Message assistant, String finalText, int turnNumber) {
        synchronized (stateLock) {
            if (terminal) return;
            messages.add(assistant);
            planTranscript.addAll(messages.subList(stepTranscriptStart, messages.size()));
            /** 带实际工具产物的步骤报告。 */
            String report = stepReport(finalText, messages.subList(stepTranscriptStart, messages.size()));
            /** 任何工具错误都使当前步骤保守地标记为失败。 */
            PlanStepStatus status = stepHadToolError ? PlanStepStatus.FAILED : PlanStepStatus.SUCCEEDED;
            /** 完成前的步骤序号，停止策略可能移动运行器游标。 */
            int stepNumber = planRunner.currentStep().getNumber();
            planRunner.finishCurrent(status, report);
            emitPlanLocked(AgentEventType.PLAN_STEP_FINISHED, stepNumber, status, report);
        }
        advancePlan(turnNumber);
    }

    /**
     * 记录步骤模型失败，按显式策略停止或继续。
     *
     * @param code 模型错误码
     * @param error 模型错误
     * @param turnNumber 当前全局模型回合数
     */
    private void handleModelFailure(String code, Throwable error, int turnNumber) {
        synchronized (stateLock) {
            if (terminal) return;
            if (planRunner == null) {
                fail(code, error);
                return;
            }
            /** 可传给后续步骤的真实错误说明。 */
            String detail = stepReport(code + ": " + error.getMessage(),
                    messages.subList(stepTranscriptStart, messages.size()));
            /** 完成前的步骤序号，停止策略可能移动运行器游标。 */
            int stepNumber = planRunner.currentStep().getNumber();
            planRunner.finishCurrent(PlanStepStatus.FAILED, detail);
            emitPlanLocked(AgentEventType.PLAN_STEP_FINISHED, stepNumber, PlanStepStatus.FAILED, detail);
        }
        advancePlan(turnNumber);
    }

    /**
     * 汇总计划并回传与 create_plan 配对的工具结果。
     *
     * @param turnNumber 最近完成的全局模型回合数
     */
    private void finishPlan(int turnNumber) {
        /** 程序生成的完整计划报告。 */
        String summary = planRunner.summary();
        /** 计划是否全部成功。 */
        boolean succeeded = planRunner.succeeded();
        synchronized (stateLock) {
            if (terminal) return;
            /** 停止策略跳过的步骤也产生明确进度事件。 */
            List<PlanStepStatus> statuses = planRunner.statuses();
            /** 待发布跳过事件的步骤索引。 */
            for (int index = 0; index < statuses.size(); index++) {
                if (statuses.get(index) == PlanStepStatus.SKIPPED) {
                    emitPlanLocked(AgentEventType.PLAN_STEP_FINISHED, index + 1,
                            PlanStepStatus.SKIPPED, "因前序步骤失败而跳过");
                }
            }
            emitPlanLocked(AgentEventType.PLAN_FINISHED, 0, null, summary);
            if (!succeeded) {
                planFailed = true;
                failedPlanSummary = summary;
            }
            messages = parentMessages;
            exchange = parentExchange;
            planRunner = null;
        }
        /** 与计划工具调用配对的真实状态。 */
        ToolResult result = new ToolResult(planCall.getCallId(),
                succeeded ? ToolResultStatus.SUCCESS : ToolResultStatus.ERROR,
                summary, succeeded ? null : "PLAN_FAILED", false);
        emitToolEvent(new ToolExecutionEvent(planCall.getCallId(), planCall.getName(),
                succeeded ? ToolExecutionPhase.COMPLETED : ToolExecutionPhase.FAILED, result));
        appendOrchestrationToolResult(result);
        scheduleModel(turnNumber + 1);
    }

    /**
     * 将步骤的模型回答和实际工具结果合成下一步可见的报告。
     *
     * @param finalText 模型给出的步骤回答
     * @param transcript 本步骤完整对话
     * @return 含工具产物与失败码的步骤报告
     */
    private static String stepReport(String finalText, List<Message> transcript) {
        /** 附有工具结果的步骤报告。 */
        StringBuilder report = new StringBuilder(finalText);
        /** 步骤中产生的模型消息。 */
        for (Message message : transcript) {
            /** 当前模型消息的真实工具结果。 */
            for (ToolResult result : message.getToolResults()) {
                report.append("\n工具 ").append(result.getCallId()).append(" [")
                        .append(result.getStatus()).append("]: ").append(result.getContent());
            }
        }
        return report.toString();
    }

    /**
     * 将一次编排工具结果加入当前消息和适用的父级交换。
     *
     * @param result 计划或子 Agent 工具结果
     */
    private void appendOrchestrationToolResult(ToolResult result) {
        synchronized (stateLock) {
            if (terminal) return;
            if (outputCharacters + result.getContent().length()
                    > definition.getLimits().getMaxOutputCharacters()) {
                limitExceeded("编排工具结果达到字符上限", ModelFinishReason.TOOL_CALLS);
                return;
            }
            outputCharacters += result.getContent().length();
            /** 和编排调用配对的工具消息。 */
            Message toolMessage = new Message(runId + ":orchestration:tool:" + result.getCallId(), Role.TOOL,
                    Collections.emptyList(), Collections.emptyList(), Collections.singletonList(result),
                    Collections.emptyMap());
            messages.add(toolMessage);
            if (planRunner == null) {
                exchange.add(toolMessage);
            }
        }
    }

    /**
     * 发布当前计划的带结构化步骤状态事件。
     *
     * @param type 计划事件种类
     * @param stepNumber 步骤序号；整体事件为零
     * @param status 步骤状态；整体事件为 null
     * @param detail 任务说明或真实结果
     */
    private void emitPlanLocked(AgentEventType type, int stepNumber, PlanStepStatus status, String detail) {
        eventPublisher.publish(new AgentEvent(runId, sessionId, ++eventSequence, Instant.now(), type,
                null, null, null, null, new PlanEvent(planRunner.getPlan().getName(), stepNumber,
                planRunner.getPlan().getSteps().size(), status, detail)));
    }

    /**
     * 只有完整交换可提交时才取得终态；失败计划保留失败状态。
     *
     * @param assistant 完整助手回答
     * @param finalText 完整文本
     * @param reason 正常结束原因
     */
    private void finishSuccess(Message assistant, String finalText, ModelFinishReason reason) {
        /** 唯一成功结果。 */
        AgentResult result;
        synchronized (stateLock) {
            if (terminal) {
                return;
            }
            if (deadlineReached()) {
                timeout();
                return;
            }
            exchange.add(assistant);
            try {
                session.appendExchange(sessionVersion, exchange);
            } catch (RuntimeException exception) {
                fail("PERSISTENCE_ERROR", exception);
                return;
            }
            result = planFailed
                    ? new AgentResult(runId, sessionId, request.getRequestId(), AgentResultStatus.FAILED,
                    null, reason, new AgentError("PLAN_FAILED", failedPlanSummary + "\n最终说明：" + finalText),
                    template.getTemplateId(), template.getContentHash(), usageTotal)
                    : new AgentResult(runId, sessionId, request.getRequestId(), AgentResultStatus.COMPLETED,
                    finalText, reason, null, template.getTemplateId(), template.getContentHash(), usageTotal);
            terminal = true;
            releaseLocked();
            emitLocked(planFailed ? AgentEventType.FAILED : AgentEventType.COMPLETED,
                    null, result, null, null);
        }
        close(result, false);
    }

    /**
     * 将工具阶段事件关联到当前运行，终态后忽略迟到事件。
     *
     * @param toolEvent 工具执行阶段
     */
    private void emitToolEvent(ToolExecutionEvent toolEvent) {
        synchronized (stateLock) {
            if (!terminal) {
                emitLocked(AgentEventType.TOOL_EXECUTION, null, null, toolEvent, null);
            }
        }
    }

    /**
     * 对超时触发唯一终态。
     */
    private void timeout() {
        stop(AgentResultStatus.TIMED_OUT, AgentEventType.TIMED_OUT, "TIMEOUT", "运行超过全局截止时间", null);
    }

    /**
     * 对预算耗尽触发唯一终态。
     *
     * @param message 超限说明
     * @param reason 最近的模型结束原因
     */
    private void limitExceeded(String message, ModelFinishReason reason) {
        stop(AgentResultStatus.LIMIT_EXCEEDED, AgentEventType.LIMIT_EXCEEDED, "LIMIT_EXCEEDED", message, reason);
    }

    /**
     * 把执行控制异常映射为取消、超时或预算终态。
     *
     * @param error 控制异常
     */
    private void stopForControl(ExecutionControlException error) {
        if ("TIMEOUT".equals(error.getCode())) {
            timeout();
        } else if ("LIMIT_EXCEEDED".equals(error.getCode())) {
            limitExceeded(error.getMessage(), null);
        } else {
            cancel();
        }
    }

    /**
     * 将普通失败转换为确定的失败结果。
     *
     * @param code 稳定错误码
     * @param error 原因
     */
    private void fail(String code, Throwable error) {
        /** 无异常消息时使用类名。 */
        String message = StringUtils.isBlank(error.getMessage())
                ? error.getClass().getSimpleName() : error.getMessage();
        stop(AgentResultStatus.FAILED, AgentEventType.FAILED, code, message, null);
    }

    /**
     * 使所有失败或中断路径竞争同一个终态。
     *
     * @param status 最终状态
     * @param type 最终事件类型
     * @param code 错误码
     * @param message 错误信息
     * @param reason 可选模型结束原因
     */
    private void stop(AgentResultStatus status, AgentEventType type, String code,
                      String message, ModelFinishReason reason) {
        /** 唯一停止结果。 */
        AgentResult result;
        synchronized (stateLock) {
            if (terminal) {
                return;
            }
            terminal = true;
            result = new AgentResult(runId, sessionId, request.getRequestId(), status, null, reason,
                    new AgentError(code, message), template.getTemplateId(), template.getContentHash(), usageTotal);
            releaseLocked();
            emitLocked(type, null, result, null, null);
        }
        close(result, true);
    }

    /**
     * 在状态锁内释放会话和取消已安排的全局计时器。
     */
    private void releaseLocked() {
        if (ownsSession) {
            session.release();
            ownsSession = false;
        }
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
    }

    /**
     * 完成观察者与 Future，并向仍在运行的资源传播取消。
     *
     * @param result 唯一结果
     * @param interruptResources 是否停止阻塞资源
     */
    private void close(AgentResult result, boolean interruptResources) {
        eventPublisher.complete();
        completion.complete(result);
        if (interruptResources) {
            cancellation.cancel();
        }
    }

    /**
     * 在状态锁内赋予事件单调递增序号并发布。
     *
     * @param type 事件种类
     * @param text 文本增量
     * @param result 最终结果
     * @param toolEvent 工具阶段
     * @param usage 单轮用量
     */
    private void emitLocked(AgentEventType type, String text, AgentResult result,
                            ToolExecutionEvent toolEvent, ModelTokenUsage usage) {
        eventPublisher.publish(new AgentEvent(runId, sessionId, ++eventSequence, Instant.now(),
                type, text, result, toolEvent, usage));
    }

    /**
     * 判断全局截止时间是否已到。
     *
     * @return 已过期时为 true
     */
    private boolean deadlineReached() {
        return deadline != null && !Instant.now().isBefore(deadline);
    }

    /**
     * 对完整助手文本和工具参数计数，防止无增量网关绕过预算。
     *
     * @param assistant 完整助手消息
     * @return UTF-16 字符数
     */
    private static long countContent(Message assistant) {
        /** 完整模型输出大小。 */
        long count = 0;
        for (ContentBlock block : assistant.getContentBlocks()) {
            if (!(block instanceof TextContentBlock)) {
                throw new IllegalArgumentException("当前仅支持文本助手消息");
            }
            count += ((TextContentBlock) block).getText().length();
        }
        for (ToolCall call : assistant.getToolCalls()) {
            count += call.getName().length() + call.getArgumentsJson().length();
        }
        return count;
    }

    /**
     * 从完整助手消息提取最终文本。
     *
     * @param message 完整助手消息
     * @return 完整回答
     */
    private static String getText(Message message) {
        /** 依次拼接内容块的最终文本。 */
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : message.getContentBlocks()) {
            text.append(((TextContentBlock) block).getText());
        }
        if (StringUtils.isBlank(text.toString())) {
            throw new IllegalArgumentException("完整助手消息没有文本内容");
        }
        return text.toString();
    }

    /**
     * 将文本包装成协议中立消息。
     *
     * @param messageId 消息标识
     * @param role 消息角色
     * @param text 文本
     * @return 完整消息
     */
    private static Message textMessage(String messageId, Role role, String text) {
        return new Message(messageId, role, Collections.singletonList(new TextContentBlock(text)),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * 隔离单个模型回合的完整消息、增量与唯一回调终态。
     */
    private final class RoundListener implements ModelEventListener {
        /** 当前回合编号。 */
        private final int turnNumber;
        /** 本回合最终完整结果。 */
        private ModelTurn turn;
        /** 本回合已经发布的模型字符数。 */
        private long streamedCharacters;

        /**
         * 创建本轮监听器。
         *
         * @param turnNumber 从一开始的回合编号
         */
        private RoundListener(int turnNumber) {
            this.turnNumber = turnNumber;
        }

        /**
         * 接纳一次模型增量；完成回合在 onComplete 后才处理。
         *
         * @param event 模型事件
         */
        @Override
        public void onEvent(ModelEvent event) {
            synchronized (stateLock) {
                if (terminal) {
                    return;
                }
                if (deadlineReached()) {
                    timeout();
                    return;
                }
                if (event instanceof TextDelta || event instanceof ReasoningDelta) {
                    /** 当前增量的文本。 */
                    String text = event instanceof TextDelta ? ((TextDelta) event).getText()
                            : ((ReasoningDelta) event).getText();
                    if (outputCharacters + streamedCharacters + text.length()
                            > definition.getLimits().getMaxOutputCharacters()) {
                        limitExceeded("模型输出达到字符上限", null);
                        return;
                    }
                    streamedCharacters += text.length();
                    emitLocked(event instanceof TextDelta ? AgentEventType.TEXT_DELTA
                            : AgentEventType.REASONING_DELTA, text, null, null, null);
                } else if (event instanceof TurnCompleted) {
                    turn = ((TurnCompleted) event).getTurn();
                } else if (!(event instanceof UsageReported)) {
                    throw new IllegalArgumentException("当前 Agent 阶段不支持该模型事件");
                }
            }
        }

        /**
         * 模型调用失败时尝试取得运行的失败终态。
         *
         * @param error 模型错误
         */
        @Override
        public void onError(Throwable error) {
            if (error instanceof ExecutionControlException) {
                stopForControl((ExecutionControlException) error);
            } else if (error instanceof ModelGatewayException) {
                handleModelFailure(((ModelGatewayException) error).getCode(), error, turnNumber);
            } else {
                handleModelFailure("MODEL_FAILURE", error, turnNumber);
            }
        }

        /**
         * 在模型回调线程外处理工具或最终提交。
         */
        @Override
        public void onComplete() {
            try {
                executor.execute(() -> completeRound(turn, turnNumber, streamedCharacters));
            } catch (RuntimeException exception) {
                handleModelFailure("PREPARATION_FAILURE", exception, turnNumber);
            }
        }
    }
}
