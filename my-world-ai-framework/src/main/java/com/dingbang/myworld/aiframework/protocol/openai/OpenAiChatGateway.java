package com.dingbang.myworld.aiframework.protocol.openai;

import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.ModelTokenUsage;
import com.dingbang.myworld.aiframework.api.ModelToolDefinition;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.ReasoningDelta;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.api.event.UsageReported;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 将协议中立请求转换为单次 OpenAI Chat 流式 HTTP 调用。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class OpenAiChatGateway implements ModelGateway {

    /** 不可变的模型实例注册表。 */
    private final Map<String, OpenAiChatModelConfig> models;

    /** 发送单次 HTTP 请求的客户端。 */
    private final HttpClient client;

    /** JSON 序列化与解析器。 */
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
        /** 按 modelId 索引的配置。 */
        Map<String, OpenAiChatModelConfig> indexed = new LinkedHashMap<>();
        /** 当前待注册的模型实例。 */
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
        try {
            /** 由本地 modelId 选中的固定模型实例。 */
            OpenAiChatModelConfig config = models.get(request.getModelId());
            if (config == null) {
                throw new IllegalArgumentException("未知模型标识: " + request.getModelId());
            }
            /** 构造已校验的完整请求体。 */
            String body = mapper.writeValueAsString(buildBody(request, config));
            /** 本轮独立的 HTTP 请求。 */
            HttpRequest httpRequest = HttpRequest.newBuilder(config.getEndpoint())
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            /** 按流读取的 HTTP 响应。 */
            HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            /** 由当前 HTTP 响应持有的流。 */
            try (InputStream stream = response.body()) {
                if (response.statusCode() != 200) {
                    throw new OpenAiChatProtocolException("模型 HTTP 请求失败，状态码: " + response.statusCode(),
                            response.statusCode());
                }
                /** 当前请求私有的分片聚合状态。 */
                StreamAccumulator accumulator = new StreamAccumulator(listener);
                readEvents(stream, accumulator);
                listener.onEvent(new TurnCompleted(accumulator.complete()));
                listener.onComplete();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            listener.onError(exception);
        } catch (IOException | RuntimeException exception) {
            listener.onError(exception);
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
        /** 根请求节点。 */
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getWireModel());
        body.put("stream", true);
        body.putObject("stream_options").put("include_usage", true);
        /** 有序消息数组。 */
        ArrayNode messages = body.putArray("messages");
        /** 当前待编码的对话消息。 */
        for (Message message : request.getMessages()) {
            encodeMessage(messages, message, config);
        }
        if (!request.getTools().isEmpty()) {
            /** 模型可见的工具列表。 */
            ArrayNode tools = body.putArray("tools");
            /** 当前授权工具说明。 */
            for (ModelToolDefinition definition : request.getTools()) {
                /** 工具说明节点。 */
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                /** 工具函数节点。 */
                ObjectNode function = tool.putObject("function");
                function.put("name", definition.getName());
                function.put("description", definition.getDescription());
                /** 已在 Agent 端生成的参数 Schema。 */
                JsonNode schema = mapper.readTree(definition.getParameterSchemaJson());
                if (!schema.isObject()) {
                    throw new IllegalArgumentException("工具参数 Schema 必须是 JSON 对象");
                }
                function.set("parameters", schema);
            }
        }
        /** 覆盖本轮调用的有效生成选项。 */
        ModelOptions options = request.getOptions().overlay(config.getDefaultOptions());
        if (options.getTemperature() != null) {
            body.put("temperature", options.getTemperature());
        }
        if (options.getMaxCompletionTokens() != null) {
            body.put("max_completion_tokens", options.getMaxCompletionTokens());
        }
        if (options.getReasoningEffort() != null) {
            body.put("reasoning_effort", options.getReasoningEffort());
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
                throw new IllegalArgumentException("Chat 工具消息必须只包含一个工具结果");
            }
            /** 与调用标识配对的工具结果。 */
            ToolResult result = message.getToolResults().get(0);
            /** 协议工具结果消息。 */
            ObjectNode tool = target.addObject();
            tool.put("role", "tool");
            tool.put("tool_call_id", result.getCallId());
            /** 将错误状态也传回模型，供其修正调用。 */
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
            throw new IllegalArgumentException("非工具消息不能包含工具结果");
        }
        /** 协议对话消息。 */
        ObjectNode node = target.addObject();
        node.put("role", message.getRole().name().toLowerCase(java.util.Locale.ROOT));
        /** 合并当前只支持的文本内容块。 */
        StringBuilder content = new StringBuilder();
        /** 当前消息内容块。 */
        for (ContentBlock block : message.getContentBlocks()) {
            if (!(block instanceof TextContentBlock)) {
                throw new IllegalArgumentException("OpenAI Chat 当前仅支持文本内容块");
            }
            content.append(((TextContentBlock) block).getText());
        }
        if (content.length() > 0) {
            node.put("content", content.toString());
        } else {
            node.putNull("content");
        }
        if (!message.getToolCalls().isEmpty()) {
            /** 助手历史中的工具调用。 */
            ArrayNode calls = node.putArray("tool_calls");
            /** 当前助手工具调用。 */
            for (ToolCall call : message.getToolCalls()) {
                /** 单条调用节点。 */
                ObjectNode callNode = calls.addObject();
                callNode.put("id", call.getCallId());
                callNode.put("type", "function");
                /** 调用的函数与原始参数 JSON。 */
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
    private void readEvents(InputStream stream, StreamAccumulator accumulator) throws IOException {
        /** UTF-8 SSE 行读取器。 */
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        /** 当前事件的 data 行。 */
        StringBuilder data = new StringBuilder();
        /** 当前读取的行。 */
        String line;
        while ((line = reader.readLine()) != null) {
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
     * 聚合一次请求内的内容、推理、工具调用及最终用量。
     */
    private final class StreamAccumulator {

        /** 接收增量事件的监听器。 */
        private final ModelEventListener listener;

        /** 完整回答文本。 */
        private final StringBuilder content = new StringBuilder();

        /** 完整推理文本。 */
        private final StringBuilder reasoning = new StringBuilder();

        /** 按流内 index 排序的工具分片。 */
        private final Map<Integer, ToolParts> tools = new TreeMap<>();

        /** 服务端完成标识。 */
        private String completionId;

        /** 标准化结束原因。 */
        private ModelFinishReason finishReason;

        /** 最终用量。 */
        private ModelTokenUsage usage;

        /** 是否收到 SSE DONE 标记。 */
        private boolean done;

        /**
         * 创建当前请求的聚合器。
         *
         * @param listener 接收增量的监听器
         */
        private StreamAccumulator(ModelEventListener listener) {
            this.listener = listener;
        }

        /**
         * 解析一个 SSE data 事件。
         *
         * @param data 事件数据
         */
        private void accept(String data) throws IOException {
            if ("[DONE]".equals(data)) {
                done = true;
                return;
            }
            /** 本次事件 JSON。 */
            JsonNode chunk = mapper.readTree(data);
            if (!chunk.isObject()) {
                throw new OpenAiChatProtocolException("模型流事件必须是 JSON 对象", 0);
            }
            if (chunk.hasNonNull("error")) {
                throw new OpenAiChatProtocolException("模型流报告错误", 0);
            }
            if (chunk.hasNonNull("id")) {
                completionId = chunk.get("id").asText();
            }
            /** 本事件报告的最终用量。 */
            JsonNode usageNode = chunk.path("usage");
            if (usageNode.isObject()) {
                usage = new ModelTokenUsage(requiredLong(usageNode, "prompt_tokens"),
                        requiredLong(usageNode, "completion_tokens"), requiredLong(usageNode, "total_tokens"),
                        usageNode.path("completion_tokens_details").path("reasoning_tokens").isNumber()
                                ? usageNode.path("completion_tokens_details").path("reasoning_tokens").longValue()
                                : null);
                listener.onEvent(new UsageReported(usage));
            }
            /** 服务端提供的候选结果。 */
            JsonNode choices = chunk.path("choices");
            if (!choices.isArray()) {
                throw new OpenAiChatProtocolException("模型流缺少 choices 数组", 0);
            }
            /** 当前候选结果，协议层只接受 index 为零。 */
            for (JsonNode choice : choices) {
                if (choice.path("index").asInt(-1) != 0) {
                    throw new OpenAiChatProtocolException("当前只支持单个候选结果", 0);
                }
                /** 当前候选的增量内容。 */
                JsonNode delta = choice.path("delta");
                if (delta.isObject()) {
                    if (delta.path("content").isTextual()) {
                        /** 本次回答文本增量。 */
                        String text = delta.get("content").asText();
                        content.append(text);
                        if (!text.isEmpty()) {
                            listener.onEvent(new TextDelta(text));
                        }
                    }
                    if (delta.path("reasoning_content").isTextual()) {
                        /** 本次推理文本增量。 */
                        String text = delta.get("reasoning_content").asText();
                        reasoning.append(text);
                        if (!text.isEmpty()) {
                            listener.onEvent(new ReasoningDelta(text));
                        }
                    }
                    if (delta.path("tool_calls").isArray()) {
                        /** 当前工具调用分片。 */
                        for (JsonNode call : delta.path("tool_calls")) {
                            /** 本次工具分片对应的流内索引。 */
                            int index = call.path("index").asInt(-1);
                            if (index < 0) {
                                throw new OpenAiChatProtocolException("工具分片缺少有效 index", 0);
                            }
                            /** 当前索引的工具分片。 */
                            ToolParts parts = tools.computeIfAbsent(index, ignored -> new ToolParts());
                            if (call.path("id").isTextual()) {
                                parts.id.append(call.get("id").asText());
                            }
                            if (call.path("type").isTextual() && !"function".equals(call.get("type").asText())) {
                                throw new OpenAiChatProtocolException("不支持的工具调用类型", 0);
                            }
                            /** 函数名称和参数片段。 */
                            JsonNode function = call.path("function");
                            if (function.path("name").isTextual()) {
                                parts.name.append(function.get("name").asText());
                            }
                            if (function.path("arguments").isTextual()) {
                                parts.arguments.append(function.get("arguments").asText());
                            }
                        }
                    }
                }
                if (choice.path("finish_reason").isTextual()) {
                    if (finishReason != null) {
                        throw new OpenAiChatProtocolException("重复的模型结束原因", 0);
                    }
                    finishReason = normalizeFinishReason(choice.get("finish_reason").asText());
                }
            }
        }

        /**
         * 在 DONE 后创建唯一完整模型回合。
         *
         * @return 完整助手回合
         */
        private ModelTurn complete() {
            if (!done || finishReason == null) {
                throw new OpenAiChatProtocolException("模型流缺少 DONE 或结束原因", 0);
            }
            /** 按索引组装的完整调用列表。 */
            List<ToolCall> calls = new ArrayList<>();
            /** 当前工具索引聚合后的完整片段。 */
            for (ToolParts parts : tools.values()) {
                if (parts.id.isEmpty() || parts.name.isEmpty() || parts.arguments.isEmpty()) {
                    throw new OpenAiChatProtocolException("模型工具调用分片不完整", 0);
                }
                calls.add(new ToolCall(parts.id.toString(), parts.name.toString(), parts.arguments.toString()));
            }
            /** 回传时保留协议需要的推理历史及完成标识。 */
            Map<String, String> metadata = new LinkedHashMap<>();
            if (!reasoning.isEmpty()) {
                metadata.put("reasoning_content", reasoning.toString());
            }
            if (completionId != null) {
                metadata.put("completion_id", completionId);
            }
            /** 当前助手的非空文本块。 */
            List<ContentBlock> blocks = content.isEmpty() ? Collections.emptyList()
                    : Collections.singletonList(new TextContentBlock(content.toString()));
            /** 当前唯一的完整助手消息。 */
            Message assistant = new Message(completionId == null ? UUID.randomUUID().toString() : completionId,
                    Role.ASSISTANT, blocks, calls, Collections.emptyList(), metadata);
            return new ModelTurn(assistant, finishReason, usage);
        }
    }

    /**
     * 保存同一个工具 index 的跨事件片段。
     */
    private static final class ToolParts {
        /** 供应商调用标识的片段。 */
        private final StringBuilder id = new StringBuilder();
        /** 工具名称的片段。 */
        private final StringBuilder name = new StringBuilder();
        /** JSON 参数的片段。 */
        private final StringBuilder arguments = new StringBuilder();
    }

    /**
     * 从用量对象读取必需的非负整数。
     *
     * @param node 用量 JSON 对象
     * @param field 字段名称
     * @return token 数
     */
    private long requiredLong(JsonNode node, String field) {
        if (!node.path(field).canConvertToLong()) {
            throw new OpenAiChatProtocolException("模型用量缺少整数字段: " + field, 0);
        }
        return node.get(field).longValue();
    }

    /**
     * 把协议结束原因转为中立枚举，未知值交给上层决定失败策略。
     *
     * @param reason 协议结束原因
     * @return 标准化结束原因
     */
    private ModelFinishReason normalizeFinishReason(String reason) {
        return switch (reason) {
            case "stop" -> ModelFinishReason.STOP;
            case "tool_calls" -> ModelFinishReason.TOOL_CALLS;
            case "length" -> ModelFinishReason.LENGTH;
            case "content_filter" -> ModelFinishReason.REFUSAL;
            default -> ModelFinishReason.UNKNOWN;
        };
    }
}
