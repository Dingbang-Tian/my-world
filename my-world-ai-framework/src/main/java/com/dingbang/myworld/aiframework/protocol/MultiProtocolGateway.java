package com.dingbang.myworld.aiframework.protocol;

import com.dingbang.myworld.aiframework.model.content.MediaKind;

import com.dingbang.myworld.aiframework.api.ExecutionControlException;
import com.dingbang.myworld.aiframework.api.ModelExecutionContext;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelGatewayException;
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
import com.dingbang.myworld.aiframework.model.content.MediaContentBlock;
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
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将同一单轮模型契约映射到 Responses 和 Anthropic Messages 流协议。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class MultiProtocolGateway implements ModelGateway {
    /**
     * 按本地模型标识索引的不可变配置。
     */
    private final Map<String, MultiProtocolModelConfig> models;
    /**
     * HTTP 客户端。
     */
    private final HttpClient client;
    /**
     * JSON 编解码器。
     */
    private final ObjectMapper mapper;

    /**
     * 使用默认客户端创建协议网关。
     *
     * @param configs 模型实例配置
     */
    public MultiProtocolGateway(Collection<MultiProtocolModelConfig> configs) {
        this(configs, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    /**
     * 创建可注入网络客户端的协议网关。
     *
     * @param configs 模型实例配置
     * @param client HTTP 客户端
     * @param mapper JSON 编解码器
     */
    public MultiProtocolGateway(Collection<MultiProtocolModelConfig> configs, HttpClient client, ObjectMapper mapper) {
        // 注册期间的模型映射。
        Map<String, MultiProtocolModelConfig> indexed = new LinkedHashMap<>();
        for (MultiProtocolModelConfig config : Objects.requireNonNull(configs, "配置不能为空")) {
            if (indexed.putIfAbsent(config.getModelId(), config) != null) {
                throw new IllegalArgumentException("重复模型标识: " + config.getModelId());
            }
        }
        this.models = Map.copyOf(indexed);
        this.client = Objects.requireNonNull(client, "客户端不能为空");
        this.mapper = Objects.requireNonNull(mapper, "解析器不能为空");
    }

    /**
     * 发送一次流式请求，并只发送一个正常或错误终态。
     *
     * @param request 单轮请求
     * @param listener 模型事件监听器
     */
    @Override
    public void generate(ModelRequest request, ModelEventListener listener) {
        Objects.requireNonNull(request, "请求不能为空");
        Objects.requireNonNull(listener, "监听器不能为空");
        // 当前调用的取消和预算边界。
        ModelExecutionContext context = request.getExecutionContext();
        try {
            context.checkActive();
            // 请求选中的模型配置。
            MultiProtocolModelConfig config = models.get(request.getModelId());
            if (config == null) throw new ModelGatewayException("CONFIGURATION_ERROR", "未知模型: " + request.getModelId());
            // 已验证能力的请求体。
            ObjectNode body = buildBody(request, config);
            // 供应商 HTTP 请求。
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.getEndpoint())
                    .timeout(timeout(context)).header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            if (config.getProtocol() == MultiProtocolType.ANTHROPIC) {
                builder.header("x-api-key", config.getApiKey()).header("anthropic-version", "2023-06-01");
            } else builder.header("Authorization", "Bearer " + config.getApiKey());
            // 流式 HTTP 响应。
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                // 取消时关闭响应流的注销动作。
                Runnable unregister = context.getCancellation().onCancel(() -> {
                    try { stream.close(); } catch (IOException ignored) { /* 终态由执行边界决定。 */ }
                });
                try {
                    context.checkActive();
                    if (response.statusCode() != 200) {
                        throw new ModelGatewayException("PROVIDER_ERROR", "模型 HTTP 状态码: " + response.statusCode());
                    }
                    // 本轮独立的事件聚合器。
                    MultiProtocolAccumulator accumulator = new MultiProtocolAccumulator(mapper, config.getProtocol(), listener, context);
                    readStream(stream, accumulator, context);
                    context.checkActive();
                    listener.onEvent(new TurnCompleted(accumulator.complete()));
                    listener.onComplete();
                } finally { unregister.run(); }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            reportError(listener, context, error);
        } catch (IOException | RuntimeException error) {
            reportError(listener, context, error);
        }
    }

    /**
     * 在迟到网络错误时优先报告取消或截止时间。
     *
     * @param listener 当前监听器
     * @param context 运行边界
     * @param error 原始错误
     */
    private void reportError(ModelEventListener listener, ModelExecutionContext context, Throwable error) {
        try { context.checkActive(); listener.onError(error); }
        catch (ExecutionControlException stopped) { listener.onError(stopped); }
    }

    /**
     * 计算与全局截止时间一致的 HTTP 时限。
     *
     * @param context 运行边界
     * @return 有效请求时限
     */
    private Duration timeout(ModelExecutionContext context) {
        if (context.getDeadline() == null) return Duration.ofSeconds(90);
        // 截止前的剩余时间。
        Duration remaining = Duration.between(Instant.now(), context.getDeadline());
        if (remaining.isNegative() || remaining.isZero()) throw new ExecutionControlException("TIMEOUT", "模型调用超时");
        return remaining.compareTo(Duration.ofSeconds(90)) < 0 ? remaining : Duration.ofSeconds(90);
    }

    /**
     * 编码协议请求和模型能力检查。
     *
     * @param request 单轮请求
     * @param config 模型实例
     * @return 请求 JSON
     */
    private ObjectNode buildBody(ModelRequest request, MultiProtocolModelConfig config) {
        // 有效生成选项。
        ModelOptions options = request.getOptions().overlay(config.getDefaultOptions());
        // 当前调用真正生效的推理强度。
        String effort = Boolean.FALSE.equals(options.getThinkingEnabled()) ? null
                : options.getReasoningEffort() != null ? options.getReasoningEffort()
                : Boolean.TRUE.equals(options.getThinkingEnabled()) ? "medium" : null;
        if (effort != null && !config.isReasoningEnabled()) {
            throw new ModelGatewayException("UNSUPPORTED_CAPABILITY", "模型未启用推理选项");
        }
        // JSON 请求根节点。
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getWireModel());
        body.put("stream", true);
        if (config.getProtocol() == MultiProtocolType.RESPONSES) {
            // Responses 的输入项数组。
            ArrayNode input = body.putArray("input");
            for (Message message : request.getMessages()) encodeResponseMessage(input, message, config);
            if (options.getMaxCompletionTokens() != null) body.put("max_output_tokens", options.getMaxCompletionTokens());
            if (effort != null) body.putObject("reasoning").put("effort", effort);
            else if (Boolean.FALSE.equals(options.getThinkingEnabled()) && config.isReasoningEnabled())
                body.putObject("reasoning").put("effort", "none");
            for (ModelToolDefinition tool : request.getTools()) {
                // Responses 平铺工具定义。
                ObjectNode entry = body.withArray("tools").addObject();
                entry.put("type", "function");
                entry.put("name", tool.getName());
                entry.put("description", tool.getDescription());
                entry.set("parameters", toolSchema(tool));
            }
        } else {
            body.put("max_tokens", options.getMaxCompletionTokens() == null ? 4096 : options.getMaxCompletionTokens());
            // Anthropic 系统提示词。
            StringBuilder system = new StringBuilder();
            // Anthropic 消息数组。
            ArrayNode messages = body.putArray("messages");
            for (Message message : request.getMessages()) encodeAnthropicMessage(messages, system, message, config);
            if (!system.isEmpty()) body.put("system", system.toString());
            if (effort != null) {
                // 与推理强度对应的显式预算。
                int budget = switch (effort) {
                    case "low" -> 1024;
                    case "medium" -> 2048;
                    case "high" -> 4096;
                    default -> throw new ModelGatewayException("UNSUPPORTED_CAPABILITY", "Anthropic 推理强度只支持 low、medium、high");
                };
                if (body.path("max_tokens").asInt() <= budget) {
                    throw new ModelGatewayException("INVALID_REQUEST", "max_tokens 必须大于推理预算");
                }
                body.putObject("thinking").put("type", "enabled").put("budget_tokens", budget);
            }
            for (ModelToolDefinition tool : request.getTools()) {
                // Anthropic 工具定义。
                ObjectNode entry = body.withArray("tools").addObject();
                entry.put("name", tool.getName());
                entry.put("description", tool.getDescription());
                entry.set("input_schema", toolSchema(tool));
            }
        }
        if (options.getTemperature() != null) body.put("temperature", options.getTemperature());
        return body;
    }

    /**
     * 解析工具参数 Schema。
     *
     * @param tool 工具说明
     * @return JSON 对象 Schema
     */
    private JsonNode toolSchema(ModelToolDefinition tool) {
        try {
            // 已解析 Schema。
            JsonNode schema = mapper.readTree(tool.getParameterSchemaJson());
            if (schema == null || !schema.isObject()) throw new ModelGatewayException("INVALID_REQUEST", "工具 Schema 必须是对象");
            return schema;
        } catch (IOException error) { throw new ModelGatewayException("INVALID_REQUEST", "工具 Schema JSON 无效"); }
    }

    /**
     * 把中立消息编码为 Responses 输入项。
     *
     * @param input 输入数组
     * @param message 中立消息
     * @param config 模型能力
     */
    private void encodeResponseMessage(ArrayNode input, Message message, MultiProtocolModelConfig config) {
        if (message.getRole() == Role.TOOL) {
            if (!message.getContentBlocks().isEmpty() || message.getToolResults().isEmpty()) {
                throw new ModelGatewayException("INVALID_REQUEST", "工具消息必须包含结果且不含普通内容");
            }
            for (ToolResult result : message.getToolResults()) {
                // 函数调用结果输入项。
                ObjectNode item = input.addObject();
                item.put("type", "function_call_output");
                item.put("call_id", result.getCallId());
                item.put("output", toolOutput(result));
            }
            return;
        }
        if (!message.getToolResults().isEmpty()) throw new ModelGatewayException("INVALID_REQUEST", "普通消息不能携带工具结果");
        if (message.getRole() == Role.ASSISTANT && message.getProviderMetadata().containsKey("reasoning_item_json")) {
            try {
                // 先前响应的原始加密推理项。
                JsonNode reasoning = mapper.readTree(message.getProviderMetadata().get("reasoning_item_json"));
                if (!"reasoning".equals(reasoning.path("type").asText())) throw new IOException("invalid reasoning item");
                input.add(reasoning);
            } catch (IOException error) { throw new ModelGatewayException("INVALID_REQUEST", "推理历史元数据无效"); }
        }
        // 无文本的工具回合无需插入空助手消息项。
        ArrayNode content = null;
        if (!message.getContentBlocks().isEmpty() || message.getRole() != Role.ASSISTANT
                || message.getToolCalls().isEmpty()) {
            // 当前角色的消息项。
            ObjectNode item = input.addObject();
            item.put("role", message.getRole().name().toLowerCase());
            content = item.putArray("content");
        }
        for (ContentBlock block : message.getContentBlocks()) {
            if (block instanceof TextContentBlock text) {
                content.addObject().put("type", message.getRole() == Role.ASSISTANT ? "output_text" : "input_text")
                        .put("text", text.getText());
            } else if (block instanceof MediaContentBlock media && media.getKind() == MediaKind.IMAGE
                    && message.getRole() == Role.USER && config.isImageEnabled()
                    && List.of("image/jpeg", "image/png", "image/gif", "image/webp")
                    .contains(media.getMimeType().toLowerCase())) {
                content.addObject().put("type", "input_image").put("image_url", mediaUrl(media));
            } else throw new ModelGatewayException("UNSUPPORTED_CAPABILITY", "Responses 模型不支持该角色或附件类型");
        }
        for (ToolCall call : message.getToolCalls()) {
            // 助手历史中的函数调用项。
            ObjectNode tool = input.addObject();
            tool.put("type", "function_call");
            tool.put("call_id", call.getCallId());
            tool.put("name", call.getName());
            tool.put("arguments", call.getArgumentsJson());
        }
    }

    /**
     * 把中立消息编码为 Anthropic Messages 内容块。
     *
     * @param messages 对话数组
     * @param system 系统文本缓冲区
     * @param message 中立消息
     * @param config 模型能力
     */
    private void encodeAnthropicMessage(ArrayNode messages, StringBuilder system, Message message,
                                        MultiProtocolModelConfig config) {
        if (message.getRole() == Role.SYSTEM) {
            if (!message.getToolCalls().isEmpty() || !message.getToolResults().isEmpty())
                throw new ModelGatewayException("INVALID_REQUEST", "系统消息不能携带工具");
            for (ContentBlock block : message.getContentBlocks()) {
                if (!(block instanceof TextContentBlock text)) throw new ModelGatewayException("UNSUPPORTED_CAPABILITY", "系统提示词只能是文本");
                if (!system.isEmpty()) system.append('\n');
                system.append(text.getText());
            }
            return;
        }
        // Anthropic 消息项。
        ObjectNode item = messages.addObject();
        item.put("role", message.getRole() == Role.ASSISTANT ? "assistant" : "user");
        // 消息内容块。
        ArrayNode content = item.putArray("content");
        if (message.getRole() == Role.TOOL) {
            if (!message.getContentBlocks().isEmpty() || message.getToolResults().isEmpty())
                throw new ModelGatewayException("INVALID_REQUEST", "工具消息必须包含结果且不含普通内容");
            for (ToolResult result : message.getToolResults()) {
                content.addObject().put("type", "tool_result").put("tool_use_id", result.getCallId())
                        .put("content", toolOutput(result)).put("is_error", result.getStatus()
                                != com.dingbang.myworld.aiframework.model.ToolResultStatus.SUCCESS);
            }
            return;
        }
        if (!message.getToolResults().isEmpty()) throw new ModelGatewayException("INVALID_REQUEST", "普通消息不能携带工具结果");
        if (message.getRole() == Role.ASSISTANT) {
            // 先前完整的推理文本。
            String thinking = message.getProviderMetadata().get("thinking");
            // 与推理文本匹配的签名。
            String signature = message.getProviderMetadata().get("thinking_signature");
            if ((thinking != null && (signature != null || config.isForwardUnsignedThinking())) || signature != null) {
                // 供应商推理历史块。
                ObjectNode block = content.addObject().put("type", "thinking");
                if (thinking != null) block.put("thinking", thinking);
                if (signature != null) block.put("signature", signature);
            }
        }
        for (ContentBlock block : message.getContentBlocks()) {
            if (block instanceof TextContentBlock text) content.addObject().put("type", "text").put("text", text.getText());
            else if (block instanceof MediaContentBlock media && media.getKind() == MediaKind.IMAGE
                    && message.getRole() == Role.USER && config.isImageEnabled() && media.getBytes() != null
                    && List.of("image/jpeg", "image/png", "image/gif", "image/webp")
                    .contains(media.getMimeType().toLowerCase())) {
                // Anthropic 仅编码有界内存图片。
                ObjectNode source = content.addObject().put("type", "image").putObject("source");
                source.put("type", "base64").put("media_type", media.getMimeType())
                        .put("data", Base64.getEncoder().encodeToString(media.getBytes()));
            } else throw new ModelGatewayException("UNSUPPORTED_CAPABILITY", "Anthropic 模型不支持该角色或附件来源");
        }
        for (ToolCall call : message.getToolCalls()) {
            // Anthropic 助手工具块。
            ObjectNode tool = content.addObject().put("type", "tool_use").put("id", call.getCallId())
                    .put("name", call.getName());
            try {
                // 完整工具参数 JSON。
                JsonNode arguments = mapper.readTree(call.getArgumentsJson());
                if (arguments == null || !arguments.isObject()) throw new IOException("invalid arguments");
                tool.set("input", arguments);
            } catch (IOException error) { throw new ModelGatewayException("INVALID_REQUEST", "工具参数必须是 JSON 对象"); }
        }
    }

    /**
     * 生成与结果状态一致的模型可读内容。
     *
     * @param result 结构化工具结果
     * @return JSON 字符串
     */
    private String toolOutput(ToolResult result) {
        // 结果 JSON。
        ObjectNode node = mapper.createObjectNode();
        node.put("status", result.getStatus().name());
        node.put("content", result.getContent());
        if (result.getErrorCode() != null) node.put("error_code", result.getErrorCode());
        node.put("truncated", result.isTruncated());
        return node.toString();
    }

    /**
     * 返回图片的 HTTPS URL 或 data URI。
     *
     * @param media 图片块
     * @return 可传给 Responses 的地址
     */
    private String mediaUrl(MediaContentBlock media) {
        return media.getUrl() != null ? media.getUrl().toString() : "data:" + media.getMimeType()
                + ";base64," + Base64.getEncoder().encodeToString(media.getBytes());
    }

    /**
     * 逐条读取有界 SSE 事件，EOF 不能当作正常完成。
     *
     * @param stream HTTP 响应流
     * @param accumulator 本轮聚合器
     * @param context 取消和内存预算
     * @throws IOException 流读取失败
     */
    private void readStream(InputStream stream, MultiProtocolAccumulator accumulator, ModelExecutionContext context) throws IOException {
        // UTF-8 流读取器。
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        // 当前事件的数据行。
        StringBuilder data = new StringBuilder();
        // 总流字符数。
        long total = 0;
        // 当前有界行。
        String line;
        while ((line = readLine(reader, context)) != null) {
            context.checkActive();
            total += line.length();
            if (total > Math.max(65536L, (long) context.getMaxOutputCharacters() * 32L))
                throw new ExecutionControlException("LIMIT_EXCEEDED", "协议流过大");
            if (line.isEmpty()) {
                if (!data.isEmpty()) { accumulator.accept(data.toString()); data.setLength(0); }
                if (accumulator.done) return;
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) data.append('\n');
                data.append(line.substring(5).stripLeading());
            }
        }
        if (!data.isEmpty()) accumulator.accept(data.toString());
        if (!accumulator.done) throw new ModelGatewayException("PROTOCOL_ERROR", "模型流在完成事件前断开");
    }

    /**
     * 逐字符读取有界 SSE 行。
     *
     * @param reader 字符流
     * @param context 运行边界
     * @return 一行，EOF 时为空
     * @throws IOException 读取失败
     */
    private String readLine(BufferedReader reader, ModelExecutionContext context) throws IOException {
        // 当前行。
        StringBuilder line = new StringBuilder();
        // 下一个字符。
        int next;
        while ((next = reader.read()) != -1) {
            if ((line.length() & 1023) == 0) context.checkActive();
            if (next == '\n') return line.toString();
            if (next != '\r') line.append((char) next);
            if (line.length() > Math.max(65536L, (long) context.getMaxOutputCharacters() * 8L))
                throw new ExecutionControlException("LIMIT_EXCEEDED", "协议单行过大");
        }
        return line.isEmpty() ? null : line.toString();
    }


}
