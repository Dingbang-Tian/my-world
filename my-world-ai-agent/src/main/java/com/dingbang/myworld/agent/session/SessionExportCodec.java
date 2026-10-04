package com.dingbang.myworld.agent.session;

import com.dingbang.myworld.aiframework.model.content.MediaKind;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.agent.memory.MemorySummary;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.dingbang.myworld.aiframework.model.content.MediaContentBlock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;

/**
 * 将原始文本、工具消息和可选摘要编码为有版本、无运行对象的会话 JSON。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class SessionExportCodec {
    /**
     * 当前会话导出结构版本。
     */
    public static final int SCHEMA_VERSION = 1;
    /**
     * JSON 编解码器。
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 编码完整会话快照。
     *
     * @param sessionId 会话标识
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param options 会话选项
     * @param snapshot 历史及版本
     * @return JSON 字符串
     */
    public String encode(String sessionId, String ownerId, String appId, String agentId,
                         ModelOptions options, SessionSnapshot snapshot) {
        // 根对象。
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("sessionId", sessionId);
        root.put("ownerId", ownerId);
        root.put("appId", appId);
        root.put("agentId", agentId);
        root.put("version", snapshot.getVersion());
        if (snapshot.getSummary() != null) {
            // 已提交摘要及其覆盖位置。
            ObjectNode summaryNode = root.putObject("memorySummary");
            summaryNode.put("text", snapshot.getSummary().getText());
            summaryNode.put("coveredMessageCount", snapshot.getSummary().getCoveredMessageCount());
        }
        // 模型选项对象。
        ObjectNode optionsNode = root.putObject("modelOptions");
        if (options.getTemperature() != null) optionsNode.put("temperature", options.getTemperature());
        if (options.getMaxCompletionTokens() != null) optionsNode.put("maxCompletionTokens", options.getMaxCompletionTokens());
        if (options.getReasoningEffort() != null) optionsNode.put("reasoningEffort", options.getReasoningEffort());
        if (options.getThinkingEnabled() != null) optionsNode.put("thinkingEnabled", options.getThinkingEnabled());
        // 完整消息数组。
        ArrayNode messages = root.putArray("messages");
        // 当前待导出的历史消息。
        for (Message message : snapshot.getMessages()) {
            // 当前消息对象。
            ObjectNode node = messages.addObject();
            node.put("messageId", message.getMessageId());
            node.put("role", message.getRole().name());
            // 文本内容数组。
            ArrayNode texts = node.putArray("texts");
            // 当前消息的内容块。
            for (ContentBlock block : message.getContentBlocks()) {
                if (block instanceof TextContentBlock text) texts.add(text.getText());
                else if (!(block instanceof MediaContentBlock)) throw new IllegalArgumentException("未知内容块无法导出");
            }
            if (message.getContentBlocks().stream().anyMatch(MediaContentBlock.class::isInstance)) {
                // 保持混合内容块原有顺序的扩展数组。
                ArrayNode contentNodes = node.putArray("contentBlocks");
                for (ContentBlock block : message.getContentBlocks()) {
                    // 单个内容块记录。
                    ObjectNode contentNode = contentNodes.addObject();
                    if (block instanceof TextContentBlock text) {
                        contentNode.put("type", "text").put("text", text.getText());
                    } else if (block instanceof MediaContentBlock media) {
                        contentNode.put("type", "media").put("kind", media.getKind().name())
                                .put("mimeType", media.getMimeType()).put("name", media.getName());
                        if (media.getUrl() != null) contentNode.put("url", media.getUrl().toString());
                        else contentNode.put("base64", Base64.getEncoder().encodeToString(media.getBytes()));
                    }
                }
            }
            // 工具调用数组。
            ArrayNode calls = node.putArray("toolCalls");
            // 当前助手消息的工具调用。
            for (ToolCall call : message.getToolCalls()) {
                // 单个工具调用。
                ObjectNode item = calls.addObject();
                item.put("callId", call.getCallId());
                item.put("name", call.getName());
                item.put("argumentsJson", call.getArgumentsJson());
            }
            // 工具结果数组。
            ArrayNode results = node.putArray("toolResults");
            // 当前工具消息的执行结果。
            for (ToolResult result : message.getToolResults()) {
                // 单个工具结果。
                ObjectNode item = results.addObject();
                item.put("callId", result.getCallId());
                item.put("status", result.getStatus().name());
                item.put("content", result.getContent());
                if (result.getErrorCode() != null) item.put("errorCode", result.getErrorCode());
                item.put("truncated", result.isTruncated());
            }
            // 只导出模型续问需要的已知协议元数据。
            ObjectNode metadata = node.putObject("providerMetadata");
            // 当前协议元数据条目。
            for (Map.Entry<String, String> entry : message.getProviderMetadata().entrySet()) {
                if (!allowedMetadata(entry.getKey())) {
                    throw new IllegalArgumentException("导出格式不支持的协议元数据");
                }
                metadata.put(entry.getKey(), entry.getValue());
            }
        }
        return root.toString();
    }

    /**
     * 解码并校验版本、归属与完整消息。
     *
     * @param serialized 会话 JSON
     * @return 已校验的数据
     */
    public ImportedSession decode(String serialized) {
        return decode(serialized, false);
    }

    /**
     * 解码运行检查点中尚未以最终助手消息结束的交换。
     *
     * @param serialized 运行检查点 JSON
     * @return 可用于恢复的消息及身份
     */
    public ImportedSession decodeCheckpoint(String serialized) {
        return decode(serialized, true);
    }

    /**
     * 读取完整会话或允许未结束交换的检查点。
     *
     * @param serialized 会话 JSON
     * @param allowIncomplete 是否允许末尾交换未结束
     * @return 已解码状态
     */
    private ImportedSession decode(String serialized, boolean allowIncomplete) {
        try {
            // 根 JSON 对象。
            JsonNode root = mapper.readTree(serialized);
            if (root == null || !root.isObject() || !root.path("schemaVersion").isIntegralNumber()
                    || root.path("schemaVersion").intValue() != SCHEMA_VERSION) {
                throw new IllegalArgumentException("不支持的会话 schemaVersion");
            }
            // 模型选项节点。
            JsonNode optionsNode = root.path("modelOptions");
            if (!optionsNode.isObject()) throw new IllegalArgumentException("缺少会话选项");
            // 导入的模型选项。
            ModelOptions options = new ModelOptions(
                    optionsNode.has("temperature") ? requiredDouble(optionsNode, "temperature") : null,
                    optionsNode.has("maxCompletionTokens") ? requiredInteger(optionsNode, "maxCompletionTokens") : null,
                    optionsNode.has("reasoningEffort") ? requiredText(optionsNode, "reasoningEffort") : null,
                    optionsNode.has("thinkingEnabled") ? requiredBoolean(optionsNode, "thinkingEnabled") : null);
            // 导入的历史节点。
            JsonNode messageNodes = root.path("messages");
            if (!messageNodes.isArray()) throw new IllegalArgumentException("缺少消息数组");
            // 重建后的消息。
            List<Message> messages = new ArrayList<>();
            // 当前导入的消息节点。
            for (JsonNode node : messageNodes) {
                // 文本内容块。
                List<ContentBlock> blocks = new ArrayList<>();
                if (node.path("contentBlocks").isArray()) {
                    // 当前扩展内容块。
                    for (JsonNode contentNode : node.path("contentBlocks")) {
                        if ("text".equals(requiredText(contentNode, "type"))) {
                            blocks.add(new TextContentBlock(requiredText(contentNode, "text")));
                        } else if ("media".equals(requiredText(contentNode, "type"))) {
                            // 媒体类型。
                            MediaKind kind = MediaKind.valueOf(requiredText(contentNode, "kind"));
                            // 可选 HTTPS 来源。
                            URI url = contentNode.path("url").isTextual()
                                    ? URI.create(requiredText(contentNode, "url")) : null;
                            // 可选内存内容。
                            if (contentNode.path("base64").isTextual()
                                    && contentNode.path("base64").asText().length()
                                    > MediaContentBlock.MAX_BYTES * 4L / 3L + 8) {
                                throw new IllegalArgumentException("附件数据超出大小上限");
                            }
                            byte[] bytes = contentNode.path("base64").isTextual()
                                    ? Base64.getDecoder().decode(requiredText(contentNode, "base64")) : null;
                            blocks.add(new MediaContentBlock(kind, requiredText(contentNode, "mimeType"),
                                    requiredText(contentNode, "name"), url, bytes));
                        } else throw new IllegalArgumentException("未知内容块类型");
                    }
                } else {
                    // 旧会话中的文本节点。
                    for (JsonNode text : requiredArray(node, "texts")) {
                        if (!text.isTextual()) throw new IllegalArgumentException("文本块类型无效");
                        blocks.add(new TextContentBlock(text.asText()));
                    }
                }
                // 工具调用。
                List<ToolCall> calls = new ArrayList<>();
                // 当前调用节点。
                for (JsonNode call : requiredArray(node, "toolCalls")) {
                    calls.add(new ToolCall(requiredText(call, "callId"), requiredText(call, "name"),
                            textValue(call, "argumentsJson")));
                }
                // 工具结果。
                List<ToolResult> results = new ArrayList<>();
                // 当前结果节点。
                for (JsonNode result : requiredArray(node, "toolResults")) {
                    results.add(new ToolResult(requiredText(result, "callId"),
                            ToolResultStatus.valueOf(requiredText(result, "status")),
                            textValue(result, "content"), optionalText(result, "errorCode"),
                            requiredBoolean(result, "truncated")));
                }
                // 允许的协议元数据。
                Map<String, String> metadata = new LinkedHashMap<>();
                // 元数据 JSON 节点。
                JsonNode metadataNode = node.path("providerMetadata");
                if (!metadataNode.isObject()) throw new IllegalArgumentException("缺少协议元数据对象");
                metadataNode.fields().forEachRemaining(entry -> {
                    if (!allowedMetadata(entry.getKey())) {
                        throw new IllegalArgumentException("不允许导入的协议元数据");
                    }
                    if (!entry.getValue().isTextual()) throw new IllegalArgumentException("协议元数据类型无效");
                    metadata.put(entry.getKey(), entry.getValue().asText());
                });
                messages.add(new Message(requiredText(node, "messageId"),
                        Role.valueOf(requiredText(node, "role")), blocks, calls, results, metadata));
            }
            if (!messages.isEmpty() && !allowIncomplete) SessionHistoryValidator.validateExchange(messages);
            // 历史版本。
            if (!root.path("version").isIntegralNumber() || !root.path("version").canConvertToLong()) {
                throw new IllegalArgumentException("会话版本类型无效");
            }
            long version = root.path("version").longValue();
            if (version < 0 || (version == 0 && !messages.isEmpty()) || (version > 0 && messages.isEmpty())) {
                throw new IllegalArgumentException("会话版本与历史不一致");
            }
            // 可选摘要节点。
            JsonNode summaryNode = root.path("memorySummary");
            // 解码后的摘要。
            MemorySummary summary = null;
            if (!summaryNode.isMissingNode()) {
                if (!summaryNode.isObject()) throw new IllegalArgumentException("摘要结构无效");
                summary = new MemorySummary(requiredText(summaryNode, "text"),
                        requiredInteger(summaryNode, "coveredMessageCount"));
                if (summary.getCoveredMessageCount() > messages.size()) {
                    throw new IllegalArgumentException("摘要覆盖位置超出历史");
                }
                SessionHistoryValidator.validateExchange(messages.subList(0, summary.getCoveredMessageCount()));
            }
            return new ImportedSession(requiredText(root, "sessionId"), requiredText(root, "ownerId"),
                    requiredText(root, "appId"), requiredText(root, "agentId"), version, options, messages, summary);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无效会话 JSON", exception);
        }
    }

    /**
     * 读取必需的文本字段。
     *
     * @param node 父节点
     * @param name 字段名称
     * @return 文本值
     */
    private static String requiredText(JsonNode node, String name) {
        // 字段节点。
        JsonNode value = node.path(name);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("缺少字段: " + name);
        return value.asText();
    }

    /**
     * 读取允许空字符串的必需文本字段。
     *
     * @param node 父节点
     * @param name 字段名称
     * @return 文本值
     */
    private static String textValue(JsonNode node, String name) {
        // 字段节点。
        JsonNode value = node.path(name);
        if (!value.isTextual()) throw new IllegalArgumentException("字段类型无效: " + name);
        return value.asText();
    }

    /**
     * 读取必需的有限数字字段。
     *
     * @param node 父节点
     * @param name 字段名称
     * @return 小数值
     */
    private static double requiredDouble(JsonNode node, String name) {
        // 字段节点。
        JsonNode value = node.path(name);
        if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException("数值字段无效: " + name);
        }
        return value.doubleValue();
    }

    /**
     * 读取必需的整数字段。
     *
     * @param node 父节点
     * @param name 字段名称
     * @return 整数值
     */
    private static int requiredInteger(JsonNode node, String name) {
        // 字段节点。
        JsonNode value = node.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("整数字段无效: " + name);
        }
        return value.intValue();
    }

    /**
     * 读取必需的布尔字段。
     *
     * @param node 父节点
     * @param name 字段名称
     * @return 布尔值
     */
    private static boolean requiredBoolean(JsonNode node, String name) {
        // 字段节点。
        JsonNode value = node.path(name);
        if (!value.isBoolean()) throw new IllegalArgumentException("布尔字段无效: " + name);
        return value.booleanValue();
    }

    /**
     * 读取可选的文本字段。
     *
     * @param node 父节点
     * @param name 字段名称
     * @return 文本或 null
     */
    private static String optionalText(JsonNode node, String name) {
        return node.has(name) ? requiredText(node, name) : null;
    }

    /**
     * 读取必需的数组字段。
     *
     * @param node 父节点
     * @param name 字段名称
     * @return 数组节点
     */
    private static JsonNode requiredArray(JsonNode node, String name) {
        // 数组节点。
        JsonNode value = node.path(name);
        if (!value.isArray()) throw new IllegalArgumentException("缺少数组: " + name);
        return value;
    }

    /**
     * 限制可导出和导入的协议历史元数据。
     *
     * @param key 元数据键
     * @return 是否是已知历史字段
     */
    private static boolean allowedMetadata(String key) {
        return "reasoning_content".equals(key) || "completion_id".equals(key)
                || "thinking".equals(key) || "thinking_signature".equals(key)
                || "reasoning_item_json".equals(key);
    }

}
