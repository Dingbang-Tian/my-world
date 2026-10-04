package com.dingbang.myworld.aiframework.protocol.openai;

import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelExecutionContext;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;
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
 * OpenAI 流式响应累积器。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class OpenAiStreamAccumulator {

    /**
     * 原运行对象，供回调访问当前状态。
     */
    private final ObjectMapper mapper;

    /**
     * 接收增量事件的监听器。
     */
    private final ModelEventListener listener;

    /**
     * 当前调用的执行保护。
     */
    private final ModelExecutionContext context;

    /**
     * 当前响应的模型内容字符数。
     */
    private long contentCharacters;

    /**
     * 完整回答文本。
     */
    private final StringBuilder content = new StringBuilder();

    /**
     * 完整推理文本。
     */
    private final StringBuilder reasoning = new StringBuilder();

    /**
     * 按流内 index 排序的工具分片。
     */
    private final Map<Integer, OpenAiChatGatewayToolParts> tools = new TreeMap<>();

    /**
     * 服务端完成标识。
     */
    private String completionId;

    /**
     * 标准化结束原因。
     */
    private ModelFinishReason finishReason;

    /**
     * 最终用量。
     */
    private ModelTokenUsage usage;

    /**
     * 是否收到 SSE DONE 标记。
     */
    boolean done;

    /**
     * 解析一个 SSE data 事件。
     *
     * @param data 事件数据
     */
    void accept(String data) throws IOException {
        context.checkActive();
        if ("[DONE]".equals(data)) {
            done = true;
            return;
        }
        // 本次事件 JSON。
        JsonNode chunk;
        try {
            chunk = mapper.readTree(data);
        } catch (IOException exception) {
            throw new OpenAiChatProtocolException("模型流 JSON 无效", 0);
        }
        if (chunk == null || !chunk.isObject()) {
            throw new OpenAiChatProtocolException("模型流事件必须是 JSON 对象", 0);
        }
        if (chunk.hasNonNull("error")) {
            throw new OpenAiChatProtocolException("模型流报告错误", 0);
        }
        if (chunk.hasNonNull("id")) {
            completionId = chunk.get("id").asText();
        }
        // 本事件报告的最终用量。
        JsonNode usageNode = chunk.path("usage");
        if (usageNode.isObject()) {
            usage = new ModelTokenUsage(requiredLong(usageNode, "prompt_tokens"),
                    requiredLong(usageNode, "completion_tokens"), requiredLong(usageNode, "total_tokens"),
                    usageNode.path("completion_tokens_details").path("reasoning_tokens").isNumber()
                            ? usageNode.path("completion_tokens_details").path("reasoning_tokens").longValue()
                            : null);
            listener.onEvent(new UsageReported(usage));
        }
        // 服务端提供的候选结果。
        JsonNode choices = chunk.path("choices");
        if (!choices.isArray()) {
            throw new OpenAiChatProtocolException("模型流缺少 choices 数组", 0);
        }
        // 当前候选结果，协议层只接受 index 为零。
        for (JsonNode choice : choices) {
            if (choice.path("index").asInt(-1) != 0) {
                throw new OpenAiChatProtocolException("当前只支持单个候选结果", 0);
            }
            // 当前候选的增量内容。
            JsonNode delta = choice.path("delta");
            if (delta.isObject()) {
                if (delta.path("content").isTextual()) {
                    // 本次回答文本增量。
                    String text = delta.get("content").asText();
                    count(text);
                    content.append(text);
                    if (!text.isEmpty()) {
                        listener.onEvent(new TextDelta(text));
                    }
                }
                if (delta.path("reasoning_content").isTextual()) {
                    // 本次推理文本增量。
                    String text = delta.get("reasoning_content").asText();
                    count(text);
                    reasoning.append(text);
                    if (!text.isEmpty()) {
                        listener.onEvent(new ReasoningDelta(text));
                    }
                }
                if (delta.path("tool_calls").isArray()) {
                    // 当前工具调用分片。
                    for (JsonNode call : delta.path("tool_calls")) {
                        // 本次工具分片对应的流内索引。
                        int index = call.path("index").asInt(-1);
                        if (index < 0) {
                            throw new OpenAiChatProtocolException("工具分片缺少有效 index", 0);
                        }
                        // 当前索引的工具分片。
                        OpenAiChatGatewayToolParts parts = tools.computeIfAbsent(index, ignored -> new OpenAiChatGatewayToolParts());
                        if (call.path("id").isTextual()) {
                            count(call.get("id").asText());
                            parts.id.append(call.get("id").asText());
                        }
                        if (call.path("type").isTextual() && !"function".equals(call.get("type").asText())) {
                            throw new OpenAiChatProtocolException("不支持的工具调用类型", 0);
                        }
                        // 函数名称和参数片段。
                        JsonNode function = call.path("function");
                        if (function.path("name").isTextual()) {
                            count(function.get("name").asText());
                            parts.name.append(function.get("name").asText());
                        }
                        if (function.path("arguments").isTextual()) {
                            count(function.get("arguments").asText());
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
     * 累加模型内容并在追加到缓冲区前检查上限。
     *
     * @param value 新的模型内容片段
     */
    private void count(String value) {
        contentCharacters += value.length();
        if (contentCharacters > context.getMaxOutputCharacters()) {
            throw new ExecutionControlException("LIMIT_EXCEEDED", "模型输出达到字符上限");
        }
    }

    /**
     * 在 DONE 后创建唯一完整模型回合。
     *
     * @return 完整助手回合
     */
    ModelTurn complete() {
        if (!done || finishReason == null) {
            throw new OpenAiChatProtocolException("模型流缺少 DONE 或结束原因", 0);
        }
        // 按索引组装的完整调用列表。
        List<ToolCall> calls = new ArrayList<>();
        // 当前工具索引聚合后的完整片段。
        for (OpenAiChatGatewayToolParts parts : tools.values()) {
            if (parts.id.isEmpty() || parts.name.isEmpty() || parts.arguments.isEmpty()) {
                throw new OpenAiChatProtocolException("模型工具调用分片不完整", 0);
            }
            calls.add(new ToolCall(parts.id.toString(), parts.name.toString(), parts.arguments.toString()));
        }
        // 回传时保留协议需要的推理历史及完成标识。
        Map<String, String> metadata = new LinkedHashMap<>();
        if (!reasoning.isEmpty()) {
            metadata.put("reasoning_content", reasoning.toString());
        }
        if (completionId != null) {
            metadata.put("completion_id", completionId);
        }
        // 当前助手的非空文本块。
        List<ContentBlock> blocks = content.isEmpty() ? Collections.emptyList()
                : Collections.singletonList(new TextContentBlock(content.toString()));
        // 当前唯一的完整助手消息。
        Message assistant = new Message(completionId == null ? UUID.randomUUID().toString() : completionId,
                Role.ASSISTANT, blocks, calls, Collections.emptyList(), metadata);
        return new ModelTurn(assistant, finishReason, usage);
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
    }}
