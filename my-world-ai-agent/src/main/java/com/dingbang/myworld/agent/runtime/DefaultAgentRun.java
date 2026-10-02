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
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.ModelToolDefinition;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.ValidatingModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.dingbang.myworld.common.utils.collection.CollectionUtils;
import com.dingbang.myworld.common.utils.lang.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将模型回合与可信工具执行组成唯一 Agent 循环。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
final class DefaultAgentRun implements AgentRun {

    /**

     * 最近保留的 Agent 事件数量。

     */
    private static final int EVENT_HISTORY_LIMIT = 512;

    /**

     * 本次运行标识。

     */
    private final String runId;

    /**

     * 所属会话标识。

     */
    private final String sessionId;

    /**

     * 已验证的 Agent 定义。

     */
    private final AgentDefinition definition;

    /**

     * 用户请求快照。

     */
    private final AgentRequest request;

    /**

     * 已渲染的系统消息。

     */
    private final Message systemMessage;

    /**

     * 本轮固定的模板版本。

     */
    private final PromptTemplateSnapshot template;

    /**

     * 所属进程内会话。

     */
    private final InMemoryAgentSession session;

    /**

     * 单次模型调用入口。

     */
    private final ModelGateway gateway;

    /**
     * 本次运行的工具执行器。
     */
    private final ToolExecutor toolExecutor;

    /**
     * 每次模型请求所带的工具说明。
     */
    private final List<ModelToolDefinition> modelTools;

    /**

     * 执行模型调用的 JDK 后台执行器。

     */
    private final Executor executor;

    /**

     * 对多个 Agent 观察者发布和回放事件的发布器。

     */
    private final AgentEventPublisher eventPublisher = new AgentEventPublisher(EVENT_HISTORY_LIMIT);

    /**

     * 当前运行最终结果。

     */
    private final CompletableFuture<AgentResult> completion = new CompletableFuture<>();

    /**

     * 是否已调用 execute。

     */
    private final AtomicBoolean started = new AtomicBoolean();

    /**

     * 是否已经产生唯一终态。

     */
    private final AtomicBoolean terminal = new AtomicBoolean();

    /**

     * 当前运行的事件顺序号。

     */
    private final AtomicLong eventSequence = new AtomicLong();

    /**

     * 当前运行是否占有会话。

     */
    private final AtomicBoolean ownsSession = new AtomicBoolean();

    /**
     * 本次运行生成的完整消息，成功时整体提交会话。
     */
    private List<Message> exchange;

    /**
     * 当前运行发往模型的消息，包含系统和既有历史。
     */
    private List<Message> messages;

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
     * @param executor 执行模型调用的后台执行器
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
     * 注册当前运行的事件监听器；注册本身不调用模型。
     *
     * @param listener 接收事件和结束通知的监听器
     */
    @Override
    public void subscribe(AgentEventListener listener) {
        // 发布器会先回放已有事件，再登记监听器接收后续事件。
        // 因此无论在 execute 前、运行中还是结束后注册，都不会触发第二次模型调用。
        eventPublisher.subscribe(listener);
    }

    /**
     * 返回只由内部运行完成的结果阶段。
     *
     * @return 最终结果阶段
     */
    @Override
    public CompletionStage<AgentResult> getResult() {
        // completion 只在 finish 中完成一次，所有调用方等待的是同一个最终结果。
        return completion.thenApply(result -> result);
    }

    /**
     * 唯一一次向配置的执行器提交模型调用；若执行器同步运行，此方法也会同步执行。
     *
     * @throws IllegalStateException 当前运行已经启动时
     */
    @Override
    public void execute() {
        // compareAndSet 使并发的第二次 execute 立即失败，避免重复请求模型。
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Agent 运行只能执行一次");
        }
        // 先占用会话，再准备本轮请求；同一会话同时只能有一个运行。
        if (!session.tryStart()) {
            fail("SESSION_BUSY", new IllegalStateException("会话已有运行正在执行"));
            return;
        }
        ownsSession.set(true);
        try {
            // 本轮用户文本只在这里转为 USER 消息，不会进入 SYSTEM 模板变量。
            /** 当前用户的完整消息。 */
            Message user = textMessage(runId + ":user", Role.USER, request.getUserText());
            // 历史快照在真正执行时读取，消息顺序固定为 SYSTEM、历史、当前 USER。
            messages = new ArrayList<>();
            messages.add(systemMessage);
            messages.addAll(session.getHistorySnapshot());
            messages.add(user);
            exchange = new ArrayList<>();
            exchange.add(user);
            // 由配置的 Executor 决定调用线程；异步执行器提交后立即返回。
            scheduleModel(1);
        } catch (RuntimeException exception) {
            // 准备请求或提交后台任务失败时，也必须结束事件和最终结果。
            fail("PREPARATION_FAILURE", exception);
        }
    }

    /**
     * 向配置的执行器提交下一次模型请求。
     *
     * @param turnNumber 从 1 开始的模型回合编号
     */
    private void scheduleModel(int turnNumber) {
        /** 不可变的本轮模型请求。 */
        ModelRequest modelRequest = new ModelRequest(definition.getModelId(), messages, modelTools);
        executor.execute(() -> invokeModel(modelRequest, turnNumber));
    }

    /**
     * 在后台调用一次模型，事件由本轮专属监听器接收。
     *
     * @param modelRequest 本轮模型请求
     * @param turnNumber 当前模型回合编号
     */
    private void invokeModel(ModelRequest modelRequest, int turnNumber) {
        try {
            // 验证器先拦截缺少、重复或顺序错误的 TurnCompleted，再把合法事件交给当前运行。
            ModelEventListener listener = new ValidatingModelEventListener(new RoundListener(turnNumber));
            // gateway 可以同步回调，也可以在自己的网络线程异步回调；两种情况都复用同一个 listener。
            gateway.generate(modelRequest, listener);
        } catch (RuntimeException exception) {
            // 适配器在建立调用时直接抛出的异常没有进入监听器，需在这里转换为失败结果。
            fail("MODEL_FAILURE", exception);
        }
    }

    /**
     * 根据模型结束原因执行工具或提交最终文本。
     *
     * @param turn 当前完整模型回合
     * @param turnNumber 当前回合编号
     */
    private void completeRound(ModelTurn turn, int turnNumber) {
        if (terminal.get()) {
            return;
        }
        try {
            Objects.requireNonNull(turn, "模型未返回完整回合");
            /** 完整助手消息。 */
            Message assistant = turn.getAssistantMessage();
            if (turn.getFinishReason() == ModelFinishReason.TOOL_CALLS) {
                if (CollectionUtils.isEmpty(assistant.getToolCalls())) {
                    throw new IllegalArgumentException("工具回合缺少调用");
                }
                if (turnNumber >= definition.getMaxModelTurns()) {
                    limitExceeded();
                    return;
                }
                /** 同一助手回合中已出现的调用标识。 */
                Set<String> callIds = new HashSet<>();
                for (ToolCall call : assistant.getToolCalls()) {
                    if (!callIds.add(call.getCallId())) {
                        throw new IllegalArgumentException("同一助手回合存在重复工具调用标识: " + call.getCallId());
                    }
                }
                messages.add(assistant);
                exchange.add(assistant);
                for (ToolCall call : assistant.getToolCalls()) {
                    /** 由可信运行时创建的工具上下文。 */
                    ToolExecutionContext context = new ToolExecutionContext(runId, sessionId, null, null);
                    /** 与模型调用标识配对的工具结果。 */
                    ToolResult result = toolExecutor.execute(call, context, this::emitToolEvent);
                    /** 含单个工具结果的模型消息。 */
                    Message toolMessage = new Message(runId + ":tool:" + turnNumber + ":" + call.getCallId(),
                            Role.TOOL, Collections.emptyList(), Collections.emptyList(),
                            Collections.singletonList(result), Collections.emptyMap());
                    messages.add(toolMessage);
                    exchange.add(toolMessage);
                }
                scheduleModel(turnNumber + 1);
                return;
            }
            if (CollectionUtils.isNotEmpty(assistant.getToolCalls())) {
                throw new IllegalArgumentException("非工具结束原因却包含工具调用");
            }
            if (turn.getFinishReason() != ModelFinishReason.STOP) {
                throw new IllegalArgumentException("模型没有正常完成回答: " + turn.getFinishReason());
            }
            // 最终文本从完整助手消息读取，不能由若干 TextDelta 拼接后替代。
            /** 完整助手文本。 */
            String finalText = getText(assistant);
            exchange.add(assistant);
            session.appendExchange(exchange);
            // AgentResult 保留本轮实际使用的模板版本，便于后续追踪回答来源。
            AgentResult result = new AgentResult(runId, sessionId, request.getRequestId(),
                    AgentResultStatus.COMPLETED, finalText, turn.getFinishReason(), null,
                    template.getTemplateId(), template.getContentHash());
            finish(result, AgentEventType.COMPLETED);
        } catch (RuntimeException exception) {
            // 缺少完整回合、消息内容非法等都归为模型结果无效。
            fail("INVALID_MODEL_TURN", exception);
        }
    }

    /**
     * 为工具阶段事件添加运行关联信息。
     *
     * @param toolEvent 本次工具执行阶段
     */
    private void emitToolEvent(ToolExecutionEvent toolEvent) {
        emit(AgentEventType.TOOL_EXECUTION, null, null, toolEvent);
    }

    /**
     * 在执行下一批工具前报告回合限制，避免产生无法回传的副作用。
     */
    private void limitExceeded() {
        /** 回合限制结果。 */
        AgentResult result = new AgentResult(runId, sessionId, request.getRequestId(),
                AgentResultStatus.LIMIT_EXCEEDED, null, ModelFinishReason.TOOL_CALLS,
                new AgentError("LIMIT_EXCEEDED", "模型回合数达到限制: " + definition.getMaxModelTurns()),
                template.getTemplateId(), template.getContentHash());
        finish(result, AgentEventType.LIMIT_EXCEEDED);
    }

    /**
     * 隔离每次模型请求的完整结果，防止上一轮结果污染下一轮。
     */
    private final class RoundListener implements ModelEventListener {
        /** 当前回合编号。 */
        private final int turnNumber;
        /** 本轮模型提供的完整结果。 */
        private volatile ModelTurn turn;

        /**
         * 记录当前模型回合编号。
         *
         * @param turnNumber 从 1 开始的编号
         */
        private RoundListener(int turnNumber) {
            this.turnNumber = turnNumber;
        }

        /**
         * 转发文本增量并保留完整回合。
         *
         * @param event 模型事件
         */
        @Override
        public void onEvent(ModelEvent event) {
            if (terminal.get()) {
                return;
            }
            if (event instanceof TextDelta) {
                emit(AgentEventType.TEXT_DELTA, ((TextDelta) event).getText(), null);
            } else if (event instanceof TurnCompleted) {
                turn = ((TurnCompleted) event).getTurn();
            } else {
                throw new IllegalArgumentException("当前 Agent 阶段不支持该模型事件");
            }
        }

        /**
         * 把当前模型请求失败转换成运行失败。
         *
         * @param error 模型错误
         */
        @Override
        public void onError(Throwable error) {
            fail("MODEL_FAILURE", error);
        }

        /**
         * 模型回合结束后交给唯一 Agent 循环处理。
         */
        @Override
        public void onComplete() {
            try {
                // 工具可能阻塞，交给配置的执行器而非占用模型协议回调线程。
                executor.execute(() -> completeRound(turn, turnNumber));
            } catch (RuntimeException exception) {
                fail("PREPARATION_FAILURE", exception);
            }
        }
    }

    /**
     * 将运行失败转换为确定的失败结果与终态事件。
     *
     * @param code 稳定错误类别
     * @param error 原始错误
     */
    private void fail(String code, Throwable error) {
        // 业务错误码用于程序判断；原始异常消息用于定位问题。
        String message = StringUtils.isBlank(error.getMessage())
                ? error.getClass().getSimpleName() : error.getMessage();
        // 失败也产出 AgentResult，而不是让 CompletionStage 永久异常或悬挂。
        AgentResult result = new AgentResult(runId, sessionId, request.getRequestId(),
                AgentResultStatus.FAILED, null, null, new AgentError(code, message),
                template.getTemplateId(), template.getContentHash());
        finish(result, AgentEventType.FAILED);
    }

    /**
     * 至多一次释放会话并同时结束结果和事件发布器。
     *
     * @param result 最终结果
     * @param type 终态事件种类
     */
    private void finish(AgentResult result, AgentEventType type) {
        // 模型回调、准备异常和会话忙都可能同时尝试收尾，只有第一个调用能够继续。
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        // 只有本运行成功占用过会话时才释放，SESSION_BUSY 不会误释放其他运行的会话。
        if (ownsSession.compareAndSet(true, false)) {
            session.release();
        }
        try {
            // 先发布带结果的终态事件，让事件监听器与 getResult 看到同一份结果对象。
            emit(type, null, result);
        } finally {
            // 无论监听器是否正常，事件序列和 CompletionStage 都必须结束。
            eventPublisher.complete();
            completion.complete(result);
        }
    }

    /**
     * 赋予事件运行标识、时间和递增顺序号后发布。
     *
     * @param type 事件种类
     * @param text 文本增量或 null
     * @param result 最终结果或 null
     */
    private void emit(AgentEventType type, String text, AgentResult result) {
        emit(type, text, result, null);
    }

    /**
     * 发布带可选工具阶段的事件。
     *
     * @param type 事件类型
     * @param text 文本增量
     * @param result 终态结果
     * @param toolEvent 工具执行阶段
     */
    private void emit(AgentEventType type, String text, AgentResult result,
                      ToolExecutionEvent toolEvent) {
        // 每个事件带同一 runId/sessionId 和单调递增 sequence，调用方可以按顺序重建轨迹。
        AgentEvent event = new AgentEvent(runId, sessionId, eventSequence.incrementAndGet(),
                Instant.now(), type, text, result, toolEvent);
        eventPublisher.publish(event);
    }

    /**
     * 从完整助手消息提取当前阶段唯一支持的文本结果。
     *
     * @param message 完整助手消息
     * @return 完整文本
     * @throws IllegalArgumentException 消息不含有效文本或含未支持内容时
     */
    private static String getText(Message message) {
        // 一个完整助手消息可以含多个文本块，按消息内顺序拼接。
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : message.getContentBlocks()) {
            if (!(block instanceof TextContentBlock)) {
                // 图片、音频等内容块将在后续支持，此阶段拒绝它们以避免丢失内容。
                throw new IllegalArgumentException("S04 仅支持文本助手消息");
            }
            text.append(((TextContentBlock) block).getText());
        }
        if (StringUtils.isBlank(text.toString())) {
            throw new IllegalArgumentException("完整助手消息没有文本内容");
        }
        return text.toString();
    }

    /**
     * 将非空文本包装为模型中立消息。
     *
     * @param messageId 消息标识
     * @param role 消息角色
     * @param text 文本内容
     * @return 消息对象
     */
    private static Message textMessage(String messageId, Role role, String text) {
        return new Message(messageId, role, Collections.singletonList(new TextContentBlock(text)),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }
}
