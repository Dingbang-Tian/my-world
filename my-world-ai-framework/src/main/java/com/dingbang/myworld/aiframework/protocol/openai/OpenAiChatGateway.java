package com.dingbang.myworld.aiframework.protocol.openai;

import com.dingbang.myworld.aiframework.model.content.MediaKind;

import com.dingbang.myworld.aiframework.api.ModelExecutionContext;
import com.dingbang.myworld.aiframework.api.ModelGatewayException;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.ModelToolDefinition;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.dingbang.myworld.aiframework.model.content.MediaContentBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将协议中立请求转换为单次 OpenAI Chat 流式 HTTP 调用。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class OpenAiChatGateway implements ModelGateway {

    /**
     * 不可变的模型实例注册表。
     */
    private final Map<String, OpenAiChatModelConfig> models;

    /**
     * 发送单次 HTTP 请求的客户端。
     */
    private final HttpClient client;

    /**
     * JSON 序列化与解析器。
     */
    private final ObjectMapper mapper;

    /**
     * 使用默认 HTTP 客户端注册模型实例。
     *
     * @param configs 具有唯一 modelId 的实例配置
     */
    public OpenAiChatGateway(Collection<OpenAiChatModelConfig> configs) {
        this(configs, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    /**
     * 使用可注入的 HTTP 客户端和 JSON 解析器创建网关。
     *
     * @param configs 具有唯一 modelId 的实例配置
     * @param client HTTP 客户端
     * @param mapper JSON 解析器
     */
    public OpenAiChatGateway(Collection<OpenAiChatModelConfig> configs, HttpClient client, ObjectMapper mapper) {
        Objects.requireNonNull(configs, "模型配置不能为 null");
        // 按 modelId 索引的配置。
        Map<String, OpenAiChatModelConfig> indexed = new LinkedHashMap<>();
        // 当前待注册的模型实例。
        for (OpenAiChatModelConfig config : configs) {
            Objects.requireNonNull(config, "模型配置项不能为 null");
            if (indexed.putIfAbsent(config.getModelId(), config) != null) {
                throw new IllegalArgumentException("重复模型标识: " + config.getModelId());
            }
        }
        this.models = Collections.unmodifiableMap(indexed);
        this.client = Objects.requireNonNull(client, "HTTP 客户端不能为 null");
        this.mapper = Objects.requireNonNull(mapper, "JSON 解析器不能为 null");
    }

    /**
     * 发送一次 Chat 请求，流式读取完整回合后恰好发出一个终态。
     *
     * @param request 本次请求快照
     * @param listener 模型事件监听器
     */
    @Override
    public void generate(ModelRequest request, ModelEventListener listener) {
        Objects.requireNonNull(request, "模型请求不能为 null");
        Objects.requireNonNull(listener, "模型监听器不能为 null");
        // 本次调用的取消、截止时间与输出保护。
        ModelExecutionContext context = request.getExecutionContext();
        try {
            context.checkActive();
            // 由本地 modelId 选中的固定模型实例。
            OpenAiChatModelConfig config = models.get(request.getModelId());
            if (config == null) {
                throw new ModelGatewayException("CONFIGURATION_ERROR", "未知模型标识: " + request.getModelId());
            }
            // 构造已校验的完整请求体。
            String body = mapper.writeValueAsString(buildBody(request, config));
            // 本轮独立的 HTTP 请求。
            HttpRequest httpRequest = HttpRequest.newBuilder(config.getEndpoint())
                    .timeout(requestTimeout(context))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            // 按流读取的 HTTP 响应。
            HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            // 由当前 HTTP 响应持有的流。
            try (InputStream stream = response.body()) {
                // 取消时关闭当前网络流的注销动作。
                Runnable unregister = context.getCancellation().onCancel(() -> {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                        // 关闭过程的错误由运行终态决定。
                    }
                });
                try {
                    context.checkActive();
                    if (response.statusCode() != 200) {
                        throw new OpenAiChatProtocolException("模型 HTTP 请求失败，状态码: " + response.statusCode(),
                                response.statusCode());
                    }
                    // 当前请求私有的分片聚合状态。
                    OpenAiStreamAccumulator accumulator = new OpenAiStreamAccumulator(mapper, listener, context);
                    readEvents(stream, accumulator, context);
                    context.checkActive();
                    listener.onEvent(new TurnCompleted(accumulator.complete()));
                    listener.onComplete();
                } finally {
                    unregister.run();
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            reportError(listener, context, exception);
        } catch (IOException | RuntimeException exception) {
            reportError(listener, context, exception);
        }
    }

    /**
     * 构造 Chat Completions 请求体并拒绝当前阶段不支持的内容。
     *
     * @param request 协议中立请求
     * @param config 已注册模型实例
     * @return JSON 请求节点
     */
    private ObjectNode buildBody(ModelRequest request, OpenAiChatModelConfig config) throws IOException {
        // 根请求节点。
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getWireModel());
        body.put("stream", true);
        body.putObject("stream_options").put("include_usage", true);
        // 有序消息数组。
        ArrayNode messages = body.putArray("messages");
        // 当前待编码的对话消息。
        for (Message message : request.getMessages()) {
            encodeMessage(messages, message, config);
        }
        if (!request.getTools().isEmpty()) {
            // 模型可见的工具列表。
            ArrayNode tools = body.putArray("tools");
            // 当前授权工具说明。
            for (ModelToolDefinition definition : request.getTools()) {
                // 工具说明节点。
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                // 工具函数节点。
                ObjectNode function = tool.putObject("function");
                function.put("name", definition.getName());
                function.put("description", definition.getDescription());
                // 已在 Agent 端生成的参数 Schema。
                JsonNode schema = mapper.readTree(definition.getParameterSchemaJson());
                if (!schema.isObject()) {
                    throw new ModelGatewayException("INVALID_REQUEST", "工具参数 Schema 必须是 JSON 对象");
                }
                function.set("parameters", schema);
            }
        }
        // 覆盖本轮调用的有效生成选项。
        ModelOptions options = request.getOptions().overlay(config.getDefaultOptions());
        if (options.getTemperature() != null) {
            body.put("temperature", options.getTemperature());
        }
        if (options.getMaxCompletionTokens() != null) {
            body.put("max_completion_tokens", options.getMaxCompletionTokens());
        }
        if (options.getReasoningEffort() != null && !Boolean.FALSE.equals(options.getThinkingEnabled())) {
            body.put("reasoning_effort", options.getReasoningEffort());
        }
        if (options.getThinkingEnabled() != null) {
            if (!config.isThinkingSwitchEnabled()) {
                throw new ModelGatewayException("UNSUPPORTED_CAPABILITY", "该 Chat 模型未启用推理开关");
            }
            body.putObject("chat_template_kwargs").put("enable_thinking", options.getThinkingEnabled());
        }
        return body;
    }

    /**
     * 编码一条模型消息及其中的工具交换。
     *
     * @param target 请求消息数组
     * @param message 协议中立消息
     * @param config 已选模型实例配置
     */
    private void encodeMessage(ArrayNode target, Message message, OpenAiChatModelConfig config) {
        if (message.getRole() == Role.TOOL) {
            if (!message.getContentBlocks().isEmpty() || message.getToolResults().size() != 1) {
                throw new ModelGatewayException("INVALID_REQUEST", "Chat 工具消息必须只包含一个工具结果");
            }
            // 与调用标识配对的工具结果。
            ToolResult result = message.getToolResults().get(0);
            // 协议工具结果消息。
            ObjectNode tool = target.addObject();
            tool.put("role", "tool");
            tool.put("tool_call_id", result.getCallId());
            // 将错误状态也传回模型，供其修正调用。
            ObjectNode content = mapper.createObjectNode();
            content.put("status", result.getStatus().name());
            content.put("content", result.getContent());
            if (result.getErrorCode() != null) {
                content.put("error_code", result.getErrorCode());
            }
            content.put("truncated", result.isTruncated());
            tool.put("content", content.toString());
            return;
        }
        if (!message.getToolResults().isEmpty()) {
            throw new ModelGatewayException("INVALID_REQUEST", "非工具消息不能包含工具结果");
        }
        // 协议对话消息。
        ObjectNode node = target.addObject();
        node.put("role", message.getRole().name().toLowerCase(java.util.Locale.ROOT));
        // 合并纯文本内容块。
        StringBuilder content = new StringBuilder();
        // 当前消息是否包含图片。
        boolean hasImage = message.getContentBlocks().stream().anyMatch(MediaContentBlock.class::isInstance);
        // 混合图片消息的有序内容数组。
        ArrayNode contentParts = hasImage ? node.putArray("content") : null;
        // 当前消息内容块。
        for (ContentBlock block : message.getContentBlocks()) {
            if (block instanceof TextContentBlock text) {
                content.append(text.getText());
                if (hasImage) contentParts.addObject().put("type", "text").put("text", text.getText());
            } else if (block instanceof MediaContentBlock media && hasImage
                    && config.isImageEnabled() && message.getRole() == Role.USER
                    && media.getKind() == MediaKind.IMAGE
                    && List.of("image/jpeg", "image/png", "image/gif", "image/webp")
                    .contains(media.getMimeType().toLowerCase())) {
                // 图片的 HTTPS 或 data URI。
                String url = media.getUrl() == null ? "data:" + media.getMimeType() + ";base64,"
                        + Base64.getEncoder().encodeToString(media.getBytes()) : media.getUrl().toString();
                contentParts.addObject().put("type", "image_url").putObject("image_url").put("url", url);
            } else {
                throw new ModelGatewayException("UNSUPPORTED_CAPABILITY", "OpenAI Chat 不支持该角色或附件类型");
            }
        }
        if (hasImage) {
            // 已编码为有序内容数组。
        } else if (content.length() > 0) {
            node.put("content", content.toString());
        } else {
            node.putNull("content");
        }
        if (!message.getToolCalls().isEmpty()) {
            // 助手历史中的工具调用。
            ArrayNode calls = node.putArray("tool_calls");
            // 当前助手工具调用。
            for (ToolCall call : message.getToolCalls()) {
                // 单条调用节点。
                ObjectNode callNode = calls.addObject();
                callNode.put("id", call.getCallId());
                callNode.put("type", "function");
                // 调用的函数与原始参数 JSON。
                ObjectNode function = callNode.putObject("function");
                function.put("name", call.getName());
                function.put("arguments", call.getArgumentsJson());
            }
        }
        if (config.isForwardReasoningContent()
                && message.getProviderMetadata().containsKey("reasoning_content")) {
            node.put("reasoning_content", message.getProviderMetadata().get("reasoning_content"));
        }
    }

    /**
     * 按 SSE 事件边界读取数据，只有明确的 DONE 标记才算完整传输。
     *
     * @param stream HTTP 响应流
     * @param accumulator 当前请求的分片状态
     */
    private void readEvents(InputStream stream, OpenAiStreamAccumulator accumulator,
                            ModelExecutionContext context) throws IOException {
        // UTF-8 SSE 行读取器。
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        // 当前事件的 data 行。
        StringBuilder data = new StringBuilder();
        // 用于限制心跳和 JSON 协议开销的总接收字符数。
        long wireCharacters = 0;
        // 当前读取的有界行。
        String line;
        while ((line = readBoundedLine(reader, context)) != null) {
            context.checkActive();
            wireCharacters += line.length();
            if (wireCharacters > Math.max(65536L, (long) context.getMaxOutputCharacters() * 32L)) {
                throw new ExecutionControlException("LIMIT_EXCEEDED", "模型流协议数据达到上限");
            }
            if (line.isEmpty()) {
                if (data.length() > 0) {
                    accumulator.accept(data.toString());
                    data.setLength(0);
                    if (accumulator.done) {
                        return;
                    }
                }
            } else if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring(5).stripLeading());
            }
        }
        if (data.length() > 0) {
            accumulator.accept(data.toString());
        }
        if (!accumulator.done) {
            throw new OpenAiChatProtocolException("模型流在 [DONE] 前中断", 0);
        }
    }

    /**
     * 逐字符读取 SSE 行并在分配过大内存前拒绝异常长行。
     *
     * @param reader HTTP 流读取器
     * @param context 当前调用预算
     * @return 去掉换行的行，EOF 时为 null
     * @throws IOException 读取失败时
     */
    private String readBoundedLine(BufferedReader reader, ModelExecutionContext context) throws IOException {
        // 当前有界行内容。
        StringBuilder line = new StringBuilder();
        // 单行最大允许字符数。
        long maximum = Math.max(65536L, (long) context.getMaxOutputCharacters() * 8L);
        // 下一个字符。
        int next;
        while ((next = reader.read()) != -1) {
            if ((line.length() & 1023) == 0) {
                context.checkActive();
            }
            if (next == '\n') {
                return line.toString();
            }
            if (next != '\r') {
                line.append((char) next);
                if (line.length() > maximum) {
                    throw new ExecutionControlException("LIMIT_EXCEEDED", "模型流单行达到上限");
                }
            }
        }
        return line.isEmpty() ? null : line.toString();
    }

    /**
     * 在网络错误时优先保留运行控制原因。
     *
     * @param listener 模型观察者
     * @param context 当前调用边界
     * @param error 原始网络错误
     */
    private void reportError(ModelEventListener listener, ModelExecutionContext context, Throwable error) {
        try {
            context.checkActive();
            listener.onError(error);
        } catch (ExecutionControlException stopped) {
            listener.onError(stopped);
        }
    }

    /**
     * 将 HTTP 等待时间与全局 deadline 取较短值。
     *
     * @param context 当前调用边界
     * @return HTTP 请求时限
     */
    private Duration requestTimeout(ModelExecutionContext context) {
        if (context.getDeadline() == null) {
            return Duration.ofSeconds(90);
        }
        // 全局剩余时间。
        Duration remaining = Duration.between(Instant.now(), context.getDeadline());
        if (remaining.isNegative() || remaining.isZero()) {
            throw new ExecutionControlException("TIMEOUT", "模型调用超过全局截止时间");
        }
        return remaining.compareTo(Duration.ofSeconds(90)) < 0 ? remaining : Duration.ofSeconds(90);
    }



}
