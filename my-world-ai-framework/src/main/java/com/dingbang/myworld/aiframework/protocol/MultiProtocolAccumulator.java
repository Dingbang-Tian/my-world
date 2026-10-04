package com.dingbang.myworld.aiframework.protocol;

import com.dingbang.myworld.aiframework.api.ExecutionControlException;
import com.dingbang.myworld.aiframework.api.ModelExecutionContext;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelGatewayException;
import com.dingbang.myworld.aiframework.api.ModelTokenUsage;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.ReasoningDelta;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.UsageReported;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 跨协议流式响应累积器。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MultiProtocolAccumulator {
    /**
     * 原运行对象，供回调访问当前状态。
     */
    private final ObjectMapper mapper;

    /**
     * 当前协议。
     */
    private final MultiProtocolType protocol;
    /**
     * 本轮事件监听器。
     */
    private final ModelEventListener listener;
    /**
     * 本轮执行边界。
     */
    private final ModelExecutionContext context;
    /**
     * 完整可见文本。
     */
    private final StringBuilder text = new StringBuilder();
    /**
     * 完整推理文本。
     */
    private final StringBuilder thinking = new StringBuilder();
    /**
     * 完整推理签名。
     */
    private final StringBuilder signature = new StringBuilder();
    /**
     * 按流索引排列的工具调用。
     */
    private final Map<Integer, MultiProtocolGatewayCallParts> calls = new TreeMap<>();
    /**
     * Anthropic 内容块类型。
     */
    private final Map<Integer, String> blockTypes = new LinkedHashMap<>();
    /**
     * 响应标识。
     */
    private String responseId;
    /**
     * 可回传的 Responses 原始推理项。
     */
    private String reasoningItemJson;
    /**
     * 标准化结束原因。
     */
    private ModelFinishReason finish;
    /**
     * 输入 token 数。
     */
    private Long inputTokens;
    /**
     * 输出 token 数。
     */
    private Long outputTokens;
    /**
     * 最终用量。
     */
    private ModelTokenUsage usage;
    /**
     * 计入预算的内容字符数。
     */
    private long characters;
    /**
     * 是否收到明确完成事件。
     */
    boolean done;

    /**
     * 解析一个 SSE JSON 事件。
     *
     * @param data SSE 数据
     * @throws IOException JSON 无法读取
     */
    void accept(String data) throws IOException {
        context.checkActive();
        if ("[DONE]".equals(data)) {
            if (!done) throw new ModelGatewayException("PROTOCOL_ERROR", "缺少协议完成事件");
            return;
        }
        // 当前 JSON 事件。
        JsonNode event = mapper.readTree(data);
        if (event == null || !event.isObject() || !event.path("type").isTextual())
            throw new ModelGatewayException("PROTOCOL_ERROR", "流事件缺少 type");
        if (done) throw new ModelGatewayException("PROTOCOL_ERROR", "完成事件后仍有内容");
        if (protocol == MultiProtocolType.RESPONSES) acceptResponse(event);
        else acceptAnthropic(event);
    }

    /**
     * 处理 Responses 流事件。
     *
     * @param event JSON 事件
     */
    private void acceptResponse(JsonNode event) {
        switch (event.path("type").asText()) {
            case "response.output_text.delta" -> addText(event.path("delta").asText(""));
            case "response.refusal.delta" -> finish = ModelFinishReason.REFUSAL;
            case "response.reasoning_summary_text.delta" -> addThinking(event.path("delta").asText(""));
            case "response.output_item.added" -> {
                // 新增输出项。
                JsonNode item = event.path("item");
                if ("function_call".equals(item.path("type").asText())) {
                    // 当前工具分片。
                    MultiProtocolGatewayCallParts parts = calls.computeIfAbsent(index(event, "output_index"), ignored -> new MultiProtocolGatewayCallParts());
                    parts.id = item.path("call_id").asText(null);
                    parts.name = item.path("name").asText(null);
                }
            }
            case "response.function_call_arguments.delta" -> {
                // 目标工具调用。
                MultiProtocolGatewayCallParts parts = calls.computeIfAbsent(index(event, "output_index"), ignored -> new MultiProtocolGatewayCallParts());
                addCharacters(event.path("delta").asText("").length());
                parts.arguments.append(event.path("delta").asText(""));
            }
            case "response.function_call_arguments.done" -> {
                // 已完成的工具参数。
                MultiProtocolGatewayCallParts parts = calls.computeIfAbsent(index(event, "output_index"), ignored -> new MultiProtocolGatewayCallParts());
                parts.finalArguments = event.path("arguments").asText(null);
                if (parts.arguments.isEmpty() && parts.finalArguments != null && !parts.finalCounted) {
                    addCharacters(parts.finalArguments.length());
                    parts.finalCounted = true;
                }
                if (parts.name == null) parts.name = event.path("name").asText(null);
            }
            case "response.output_item.done" -> {
                // 完成的输出项。
                JsonNode item = event.path("item");
                if ("function_call".equals(item.path("type").asText())) {
                    // 最终工具项。
                    MultiProtocolGatewayCallParts parts = calls.computeIfAbsent(index(event, "output_index"), ignored -> new MultiProtocolGatewayCallParts());
                    parts.id = item.path("call_id").asText(parts.id);
                    parts.name = item.path("name").asText(parts.name);
                    parts.finalArguments = item.path("arguments").asText(parts.finalArguments);
                    if (parts.arguments.isEmpty() && parts.finalArguments != null && !parts.finalCounted) {
                        addCharacters(parts.finalArguments.length());
                        parts.finalCounted = true;
                    }
                } else if ("reasoning".equals(item.path("type").asText())
                        && item.path("encrypted_content").isTextual()) {
                    addCharacters(item.toString().length());
                    reasoningItemJson = item.toString();
                } else if ("message".equals(item.path("type").asText()) && text.isEmpty()) {
                    for (JsonNode block : item.path("content")) {
                        if ("output_text".equals(block.path("type").asText())) addText(block.path("text").asText(""));
                    }
                }
            }
            case "response.completed" -> {
                // 完成响应对象。
                JsonNode response = event.path("response");
                responseId = response.path("id").asText(null);
                usage = responseUsage(response.path("usage"));
                finish = finish == ModelFinishReason.REFUSAL ? ModelFinishReason.REFUSAL
                        : calls.isEmpty() ? ModelFinishReason.STOP : ModelFinishReason.TOOL_CALLS;
                done = true;
            }
            case "response.incomplete" -> {
                // 未完成原因。
                String reason = event.path("response").path("incomplete_details").path("reason").asText();
                throw new ModelGatewayException("PROTOCOL_ERROR", "Responses 未完成: " + reason);
            }
            case "response.failed", "error" -> throw new ModelGatewayException("PROVIDER_ERROR", "Responses 流报告失败");
            default -> { /* 忽略与结果无关的生命周期事件。 */ }
        }
    }

    /**
     * 处理 Anthropic Messages 流事件。
     *
     * @param event JSON 事件
     */
    private void acceptAnthropic(JsonNode event) {
        switch (event.path("type").asText()) {
            case "message_start" -> {
                // 起始消息。
                JsonNode message = event.path("message");
                responseId = message.path("id").asText(null);
                if (message.path("usage").path("input_tokens").canConvertToLong())
                    inputTokens = message.path("usage").path("input_tokens").longValue();
            }
            case "content_block_start" -> {
                // 新内容块索引。
                int index = index(event, "index");
                // 新内容块。
                JsonNode block = event.path("content_block");
                // 内容块类型。
                String type = block.path("type").asText();
                blockTypes.put(index, type);
                if ("text".equals(type) && block.path("text").isTextual()) addText(block.path("text").asText());
                else if ("thinking".equals(type) && block.path("thinking").isTextual())
                    addThinking(block.path("thinking").asText());
                if ("tool_use".equals(type)) {
                    // 工具调用首片。
                    MultiProtocolGatewayCallParts parts = calls.computeIfAbsent(index, ignored -> new MultiProtocolGatewayCallParts());
                    parts.id = block.path("id").asText(null);
                    parts.name = block.path("name").asText(null);
                    if (block.path("input").isObject() && block.path("input").size() > 0)
                        parts.finalArguments = block.path("input").toString();
                } else if ("thinking".equals(type) && block.path("signature").isTextual()) {
                    addCharacters(block.path("signature").asText().length());
                    signature.append(block.path("signature").asText());
                }
            }
            case "content_block_delta" -> {
                // 增量块类型。
                String type = blockTypes.get(index(event, "index"));
                // 增量对象。
                JsonNode delta = event.path("delta");
                if ("text".equals(type) && "text_delta".equals(delta.path("type").asText()))
                    addText(delta.path("text").asText(""));
                else if ("thinking".equals(type) && "thinking_delta".equals(delta.path("type").asText()))
                    addThinking(delta.path("thinking").asText(""));
                else if ("thinking".equals(type) && "signature_delta".equals(delta.path("type").asText())) {
                    addCharacters(delta.path("signature").asText("").length());
                    signature.append(delta.path("signature").asText(""));
                }
                else if ("tool_use".equals(type) && "input_json_delta".equals(delta.path("type").asText())) {
                    // 工具参数分片。
                    String fragment = delta.path("partial_json").asText("");
                    addCharacters(fragment.length());
                    calls.get(index(event, "index")).arguments.append(fragment);
                }
            }
            case "message_delta" -> {
                // 结束增量。
                JsonNode delta = event.path("delta");
                finish = switch (delta.path("stop_reason").asText()) {
                    case "end_turn", "stop_sequence" -> ModelFinishReason.STOP;
                    case "tool_use" -> ModelFinishReason.TOOL_CALLS;
                    case "max_tokens" -> ModelFinishReason.LENGTH;
                    case "refusal" -> ModelFinishReason.REFUSAL;
                    default -> ModelFinishReason.UNKNOWN;
                };
                if (event.path("usage").path("output_tokens").canConvertToLong())
                    outputTokens = event.path("usage").path("output_tokens").longValue();
            }
            case "message_stop" -> {
                if (finish == null) throw new ModelGatewayException("PROTOCOL_ERROR", "Anthropic 缺少结束原因");
                if (inputTokens != null && outputTokens != null) {
                    usage = new ModelTokenUsage(inputTokens, outputTokens, inputTokens + outputTokens, null);
                    listener.onEvent(new UsageReported(usage));
                }
                done = true;
            }
            case "error" -> throw new ModelGatewayException("PROVIDER_ERROR", "Anthropic 流报告失败");
            default -> { /* 忽略心跳和已处理块的生命周期事件。 */ }
        }
    }

    /**
     * 从流事件读取非负索引。
     *
     * @param event 流事件
     * @param field 索引字段
     * @return 块索引
     */
    private int index(JsonNode event, String field) {
        if (!event.path(field).isInt() || event.path(field).intValue() < 0)
            throw new ModelGatewayException("PROTOCOL_ERROR", "流事件索引无效");
        return event.path(field).intValue();
    }

    /**
     * 记录文本并报告增量。
     *
     * @param delta 新文本
     */
    private void addText(String delta) {
        addCharacters(delta.length());
        text.append(delta);
        if (!delta.isEmpty()) listener.onEvent(new TextDelta(delta));
    }

    /**
     * 记录推理摘要并报告增量。
     *
     * @param delta 新推理文本
     */
    private void addThinking(String delta) {
        addCharacters(delta.length());
        thinking.append(delta);
        if (!delta.isEmpty()) listener.onEvent(new ReasoningDelta(delta));
    }

    /**
     * 统计响应字符并执行预算。
     *
     * @param count 新增长度
     */
    private void addCharacters(int count) {
        characters += count;
        if (characters > context.getMaxOutputCharacters())
            throw new ExecutionControlException("LIMIT_EXCEEDED", "模型输出达到字符上限");
    }

    /**
     * 解析 Responses 用量并报告事件。
     *
     * @param node 用量 JSON
     * @return 完整用量或 null
     */
    private ModelTokenUsage responseUsage(JsonNode node) {
        if (!node.isObject()) return null;
        if (!node.path("input_tokens").canConvertToLong() || !node.path("output_tokens").canConvertToLong())
            throw new ModelGatewayException("PROTOCOL_ERROR", "Responses 用量字段无效");
        // 输入用量。
        long input = node.path("input_tokens").longValue();
        // 输出用量。
        long output = node.path("output_tokens").longValue();
        // 推理用量。
        Long reasoning = node.path("output_tokens_details").path("reasoning_tokens").canConvertToLong()
                ? node.path("output_tokens_details").path("reasoning_tokens").longValue() : null;
        // 供应商总用量或由输入输出合成的用量。
        long total = node.path("total_tokens").canConvertToLong()
                ? node.path("total_tokens").longValue() : Math.addExact(input, output);
        // 标准化总用量。
        ModelTokenUsage result = new ModelTokenUsage(input, output, total, reasoning);
        listener.onEvent(new UsageReported(result));
        return result;
    }

    /**
     * 验证结束、工具分片后形成唯一完整回合。
     *
     * @return 完整助手回合
     */
    ModelTurn complete() {
        if (!done || finish == null) throw new ModelGatewayException("PROTOCOL_ERROR", "模型流未完整结束");
        // 完整工具调用列表。
        List<ToolCall> tools = new ArrayList<>();
        for (MultiProtocolGatewayCallParts parts : calls.values()) {
            // 完整参数文本。
            String arguments = parts.finalArguments == null ? parts.arguments.toString() : parts.finalArguments;
            if (parts.id == null || parts.id.isBlank() || parts.name == null || parts.name.isBlank()
                    || arguments.isBlank()) throw new ModelGatewayException("PROTOCOL_ERROR", "工具调用分片不完整");
            try {
                // 解析后的完整工具参数。
                JsonNode parsed = mapper.readTree(arguments);
                if (parsed == null || !parsed.isObject()) throw new IOException("invalid arguments");
            } catch (IOException error) { throw new ModelGatewayException("PROTOCOL_ERROR", "工具参数 JSON 无效"); }
            tools.add(new ToolCall(parts.id, parts.name, arguments));
        }
        if (finish == ModelFinishReason.TOOL_CALLS && tools.isEmpty())
            throw new ModelGatewayException("PROTOCOL_ERROR", "工具结束事件没有工具调用");
        if (finish == ModelFinishReason.STOP && !tools.isEmpty()) finish = ModelFinishReason.TOOL_CALLS;
        // 需保留在历史中的协议元数据。
        Map<String, String> metadata = new LinkedHashMap<>();
        if (responseId != null) metadata.put("completion_id", responseId);
        if (!thinking.isEmpty()) metadata.put(protocol == MultiProtocolType.ANTHROPIC
                ? "thinking" : "reasoning_content", thinking.toString());
        if (!signature.isEmpty()) metadata.put("thinking_signature", signature.toString());
        if (reasoningItemJson != null) metadata.put("reasoning_item_json", reasoningItemJson);
        // 助手文本内容块。
        List<ContentBlock> blocks = text.isEmpty() ? Collections.emptyList()
                : List.of(new TextContentBlock(text.toString()));
        // 完整助手消息。
        Message assistant = new Message(responseId == null ? UUID.randomUUID().toString() : responseId,
                Role.ASSISTANT, blocks, tools, Collections.emptyList(), metadata);
        return new ModelTurn(assistant, finish, usage);
    }
}
