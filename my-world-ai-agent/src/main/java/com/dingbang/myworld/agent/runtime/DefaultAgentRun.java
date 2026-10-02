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
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.ValidatingModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.dingbang.myworld.common.utils.collection.CollectionUtils;
import com.dingbang.myworld.common.utils.lang.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 把一次无工具模型回合转为同一个 Agent 运行的事件与最终结果。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
final class DefaultAgentRun implements AgentRun, ModelEventListener {

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

     * 模型事件回调中的唯一完整回合。

     */
    private volatile ModelTurn finalTurn;

    /**

     * 当前回合已构造的用户消息。

     */
    private volatile Message userMessage;

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
     * @param executor 执行模型调用的后台执行器
     */
    DefaultAgentRun(String runId, String sessionId, AgentDefinition definition, AgentRequest request,
                    Message systemMessage, PromptTemplateSnapshot template,
                    InMemoryAgentSession session, ModelGateway gateway, Executor executor) {
        this.runId = Objects.requireNonNull(runId, "运行标识不能为 null");
        this.sessionId = Objects.requireNonNull(sessionId, "会话标识不能为 null");
        this.definition = Objects.requireNonNull(definition, "Agent 定义不能为 null");
        this.request = Objects.requireNonNull(request, "用户请求不能为 null");
        this.systemMessage = Objects.requireNonNull(systemMessage, "系统消息不能为 null");
        this.template = Objects.requireNonNull(template, "模板快照不能为 null");
        this.session = Objects.requireNonNull(session, "会话不能为 null");
        this.gateway = Objects.requireNonNull(gateway, "模型入口不能为 null");
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
     * 唯一一次启动后台模型调用。
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
            Message user = textMessage(runId + ":user", Role.USER, request.getUserText());
            // 历史快照在真正执行时读取，消息顺序固定为 SYSTEM、历史、当前 USER。
            List<Message> messages = new ArrayList<>();
            messages.add(systemMessage);
            messages.addAll(session.getHistorySnapshot());
            messages.add(user);
            // ModelRequest 复制消息列表，后续代码无法修改已经准备好的本轮输入。
            ModelRequest modelRequest = new ModelRequest(definition.getModelId(), messages);
            // onComplete 没有用户消息参数，所以保存本轮的完整 USER 消息供其提交历史。
            userMessage = user;
            // execute 在提交后台任务后立即返回；模型调用和回调发生在 Executor 的线程中。
            executor.execute(() -> invokeModel(modelRequest));
        } catch (RuntimeException exception) {
            // 准备请求或提交后台任务失败时，也必须结束事件和最终结果。
            fail("PREPARATION_FAILURE", exception);
        }
    }

    /**
     * 在后台调用模型，并将模型回调转换为当前运行的终态。
     *
     * @param modelRequest 本轮模型请求
     */
    private void invokeModel(ModelRequest modelRequest) {
        try {
            // 验证器先拦截缺少、重复或顺序错误的 TurnCompleted，再把合法事件交给当前运行。
            ModelEventListener listener = new ValidatingModelEventListener(this);
            // gateway 可以同步回调，也可以在自己的网络线程异步回调；两种情况都复用同一个 listener。
            gateway.generate(modelRequest, listener);
        } catch (RuntimeException exception) {
            // 适配器在建立调用时直接抛出的异常没有进入监听器，需在这里转换为失败结果。
            fail("MODEL_FAILURE", exception);
        }
    }

    /**
     * 将模型增量转发给 Agent 观察者，并保存完整助手回合。
     *
     * @param event 一条模型事件
     */
    @Override
    public void onEvent(ModelEvent event) {
        if (event instanceof TextDelta) {
            // 增量只用于实时展示，不写入会话历史。
            emit(AgentEventType.TEXT_DELTA, ((TextDelta) event).getText(), null);
        } else if (event instanceof TurnCompleted) {
            // 完整助手消息先暂存，等 onComplete 确认模型回合正常结束后再提交历史。
            finalTurn = ((TurnCompleted) event).getTurn();
        } else {
            throw new IllegalArgumentException("当前 Agent 阶段不支持该模型事件");
        }
    }

    /**
     * 将模型调用错误转换为确定的 Agent 失败结果。
     *
     * @param error 模型调用失败原因
     */
    @Override
    public void onError(Throwable error) {
        // 模型错误统一转换为 FAILED 结果；finish 会保证错误终态只发布一次。
        fail("MODEL_FAILURE", error);
    }

    /**

     * 在模型正常结束后提交完整用户与助手消息。

     */
    @Override
    public void onComplete() {
        // 模型监听器的 onComplete 没有参数，因此取回 execute 阶段已构造的 USER 消息。
        Message user = userMessage;
        if (user == null) {
            // 正常路径一定会先设置 userMessage；缺失说明调用顺序或运行状态异常。
            fail("INVALID_MODEL_TURN", new IllegalStateException("模型完成时缺少用户消息"));
            return;
        }
        // complete 会校验 TurnCompleted，写会话历史，再产生 COMPLETED 结果。
        complete(user);
    }

    /**
     * 校验完整模型回合后写入会话历史，并发布成功结果。
     *
     * @param user 本轮用户消息
     */
    private void complete(Message user) {
        try {
            // TurnCompleted 先于 onComplete 到达，因此完整助手回合应当已经保存。
            ModelTurn turn = Objects.requireNonNull(finalTurn, "模型未返回完整回合");
            if (turn.getFinishReason() == ModelFinishReason.TOOL_CALLS
                    || CollectionUtils.isNotEmpty(turn.getAssistantMessage().getToolCalls())) {
                // S04 只有文本对话；模型要求工具时不能把它伪装成成功回答。
                fail("UNSUPPORTED_TOOL_CALL", new IllegalStateException("S04 尚未启用工具调用"));
                return;
            }
            // 最终文本从完整助手消息读取，不能由若干 TextDelta 拼接后替代。
            String finalText = getText(turn.getAssistantMessage());
            // 历史只保存完整 USER 和 ASSISTANT 两条消息，避免下一轮重复看到增量文本。
            session.appendExchange(user, turn.getAssistantMessage());
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
        // 每个事件带同一 runId/sessionId 和单调递增 sequence，调用方可以按顺序重建轨迹。
        AgentEvent event = new AgentEvent(runId, sessionId, eventSequence.incrementAndGet(),
                Instant.now(), type, text, result);
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
