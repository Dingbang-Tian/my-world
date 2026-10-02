package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentError;
import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventListener;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.prompt.PromptTemplateSnapshot;
import com.dingbang.myworld.agent.tool.ToolDescriptor;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionEvent;
import com.dingbang.myworld.agent.tool.ToolExecutor;
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
import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final InMemoryAgentSession session;
    /** 单次模型入口。 */
    private final ModelGateway gateway;
    /** 运行级授权工具执行器。 */
    private final ToolExecutor toolExecutor;
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
    /** 已接纳的模型和工具输出字符数。 */
    private long outputCharacters;
    /** 已准备执行的工具调用次数。 */
    private int toolCalls;
    /** 正在构造的本轮交换。 */
    private List<Message> exchange;
    /** 模型下一回合需要的完整上下文。 */
    private List<Message> messages;
    /** 全局截止时间。 */
    private Instant deadline;
    /** 可在任意终态撤销的超时任务。 */
    private ScheduledFuture<?> timeoutTask;

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
     * @param executor 执行模型和工具的后台执行器
     */
    DefaultAgentRun(String runId, String sessionId, AgentDefinition definition, AgentRequest request,
                    Message systemMessage, PromptTemplateSnapshot template,
                    InMemoryAgentSession session, ModelGateway gateway, ToolRegistry tools, Executor executor) {
        this.runId = Objects.requireNonNull(runId, "运行标识不能为 null");
        this.sessionId = Objects.requireNonNull(sessionId, "会话标识不能为 null");
        this.definition = Objects.requireNonNull(definition, "Agent 定义不能为 null");
        this.request = Objects.requireNonNull(request, "用户请求不能为 null");
        this.systemMessage = Objects.requireNonNull(systemMessage, "系统消息不能为 null");
        this.template = Objects.requireNonNull(template, "模板快照不能为 null");
        this.session = Objects.requireNonNull(session, "会话不能为 null");
        this.gateway = Objects.requireNonNull(gateway, "模型入口不能为 null");
        this.toolExecutor = new ToolExecutor(Objects.requireNonNull(tools, "工具注册表不能为 null"));
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
                messages.addAll(session.getHistorySnapshot());
                messages.add(user);
                exchange = new ArrayList<>();
                exchange.add(user);
                deadline = Instant.now().plus(definition.getLimits().getTimeout());
                timeoutTask = AgentExecutors.TIMER.schedule(this::timeout,
                        definition.getLimits().getTimeout().toNanos(), TimeUnit.NANOSECONDS);
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
            if (outputCharacters >= definition.getLimits().getMaxOutputCharacters()) {
                limitExceeded("运行输出达到字符上限", null);
                return;
            }
            modelRequest = new ModelRequest(definition.getModelId(), messages, modelTools,
                    ModelOptions.empty(), new ModelExecutionContext(deadline, cancellation,
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
            fail(exception.getCode(), exception);
        } catch (RuntimeException exception) {
            fail("MODEL_FAILURE", exception);
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
                    usageTotal = usageTotal == null ? turn.getUsage() : usageTotal.plus(turn.getUsage());
                    emitLocked(AgentEventType.USAGE, null, null, null, turn.getUsage());
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
            finishSuccess(assistant, finalText, turn.getFinishReason());
        } catch (ExecutionControlException exception) {
            stopForControl(exception);
        } catch (RuntimeException exception) {
            fail("INVALID_MODEL_TURN", exception);
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
            exchange.add(assistant);
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
            ToolResult result = toolExecutor.execute(call, context, this::emitToolEvent);
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
                exchange.add(toolMessage);
            }
        }
        scheduleModel(turnNumber + 1);
    }

    /**
     * 只有完整交换可提交时才取得成功终态，杜绝取消后写入历史。
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
            session.appendExchange(exchange);
            result = new AgentResult(runId, sessionId, request.getRequestId(), AgentResultStatus.COMPLETED,
                    finalText, reason, null, template.getTemplateId(), template.getContentHash(), usageTotal);
            terminal = true;
            releaseLocked();
            emitLocked(AgentEventType.COMPLETED, null, result, null, null);
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
                fail(((ModelGatewayException) error).getCode(), error);
            } else {
                fail("MODEL_FAILURE", error);
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
                fail("PREPARATION_FAILURE", exception);
            }
        }
    }
}
