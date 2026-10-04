package com.dingbang.myworld.agent.memory;

import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.aiframework.api.ModelToolDefinition;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.dingbang.myworld.aiframework.model.content.MediaContentBlock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按摘要覆盖位置组装上下文并保守估算模型输入大小。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class ContextAssembler {
    /**
     * 每条消息的角色与协议结构估算开销。
     */
    private static final int MESSAGE_OVERHEAD = 16;
    /**
     * 第一次上下文压缩时保留的工具结果字符数。
     */
    private static final int COMPACT_RESULT_CHARACTERS = 1600;
    /**
     * 强制压缩时保留的工具结果字符数。
     */
    private static final int AGGRESSIVE_RESULT_CHARACTERS = 480;
    /**
     * 常规压缩时保留的最新消息数量。
     */
    private static final int RECENT_MESSAGES = 6;

    /**
     * 将系统提示、摘要、未覆盖历史和本轮消息依次组装。
     *
     * @param system 可信系统提示
     * @param snapshot 完整历史及摘要快照
     * @param current 本轮尚未提交的消息
     * @return 模型消息副本
     */
    public List<Message> assemble(Message system, SessionSnapshot snapshot, List<Message> current) {
        // 组装后的消息。
        List<Message> result = new ArrayList<>();
        result.add(system);
        // 已提交摘要。
        MemorySummary summary = snapshot.getSummary();
        if (summary != null) {
            result.add(new Message("memory:" + summary.getCoveredMessageCount(), Role.SYSTEM,
                    List.of(new TextContentBlock("历史摘要（有损记忆）：\n" + summary.getText())),
                    List.of(), List.of(), Map.of()));
        }
        result.addAll(snapshot.getMessages().subList(summary == null ? 0 : summary.getCoveredMessageCount(),
                snapshot.getMessages().size()));
        result.addAll(current);
        return result;
    }

    /**
     * 裁剪旧工具结果，不修改用户要求、系统约束、模型元数据和最新工具批次。
     * 返回可追加列表供模型循环使用，原始历史不变；无法降至目标时由调用方拒绝超限请求。
     *
     * @param messages 当前完整模型上下文
     * @param tools 当前模型可见工具
     * @param targetTokens 压缩目标估算 token 数
     * @return 压缩后的模型消息；无需压缩时返回原消息副本
     * @throws IllegalArgumentException 压缩目标不是正数时
     */
    public List<Message> compact(List<Message> messages, List<ModelToolDefinition> tools, int targetTokens) {
        if (targetTokens < 1) throw new IllegalArgumentException("压缩目标必须为正数");
        if (estimate(messages, tools) <= targetTokens) {
            return new ArrayList<>(messages);
        }
        /** 常规压缩后的消息。 */
        List<Message> compacted = compactMessages(messages, RECENT_MESSAGES, COMPACT_RESULT_CHARACTERS);
        if (estimate(compacted, tools) <= targetTokens) {
            return compacted;
        }
        return compactMessages(compacted, 0, AGGRESSIVE_RESULT_CHARACTERS);
    }

    /**
     * 裁剪旧工具结果并完整保留最近的工具调用批次。
     *
     * @param messages 原消息
     * @param recentCount 保留末尾消息数量
     * @param resultLimit 工具结果保留长度
     * @return 新消息列表
     */
    private List<Message> compactMessages(List<Message> messages, int recentCount, int resultLimit) {
        /** 末尾完整保留的消息起始位置。 */
        int recentStart = Math.max(1, messages.size() - recentCount);
        /** 最近助手工具调用所在位置，该批所有结果必须保留。 */
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (!messages.get(index).getToolCalls().isEmpty()) {
                recentStart = Math.min(recentStart, index);
                break;
            }
        }
        /** 压缩后的消息集合。 */
        List<Message> compacted = new ArrayList<>(messages.size());
        /** 当前待处理消息索引。 */
        for (int index = 0; index < messages.size(); index++) {
            /** 当前待处理消息。 */
            Message message = messages.get(index);
            if (message.getRole() != Role.TOOL || index >= recentStart) {
                compacted.add(message);
            } else {
                compacted.add(compactMessage(message, resultLimit));
            }
        }
        return compacted;
    }

    /**
     * 裁剪成功工具输出；错误信息、调用标识、结果状态和原始存档保持不变。
     *
     * @param message 原消息
     * @param resultLimit 工具结果保留长度
     * @return 压缩消息
     */
    private Message compactMessage(Message message, int resultLimit) {
        /** 压缩后的工具结果。 */
        List<ToolResult> toolResults = new ArrayList<>();
        /** 当前工具结果。 */
        for (ToolResult result : message.getToolResults()) {
            if (result.getStatus() != com.dingbang.myworld.aiframework.model.ToolResultStatus.SUCCESS) {
                toolResults.add(result);
                continue;
            }
            /** 仅用于模型请求的有界文本。 */
            String content = shorten(result.getContent(), resultLimit);
            toolResults.add(new ToolResult(result.getCallId(), result.getStatus(),
                    content, result.getErrorCode(), result.isTruncated() || !content.equals(result.getContent())));
        }
        return new Message(message.getMessageId(), message.getRole(), message.getContentBlocks(),
                message.getToolCalls(), toolResults, message.getProviderMetadata());
    }

    /**
     * 保留旧文本首尾及有损裁剪标记，避免反复裁剪相同限额的结果。
     *
     * @param value 原文本
     * @param limit 最大字符数
     * @return 原文本或压缩后的文本
     */
    private static String shorten(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        /** 明确说明省略内容不能被视为完整事实。 */
        String marker = "\n[旧工具输出已裁剪；需要原文时重新定向读取]\n";
        /** 首部保留长度，优先保存文件哈希或退出码等元数据。 */
        int head = (limit - marker.length()) * 2 / 3;
        /** 尾部起始字符偏移。 */
        int tail = value.length() - (limit - marker.length() - head);
        if (Character.isHighSurrogate(value.charAt(head - 1))) head--;
        if (Character.isLowSurrogate(value.charAt(tail))) tail++;
        return value.substring(0, head) + marker + value.substring(tail);
    }

    /**
     * 估算消息和工具定义的 token 数；文本按 UTF-8 字节，附件按来源与大小保守计入。
     *
     * @param messages 模型消息
     * @param tools 模型可见的工具定义
     * @return 保守估算 token 数
     */
    public int estimate(List<Message> messages, List<ModelToolDefinition> tools) {
        // 消息、工具及协议开销的累计估算。
        long total = 0;
        // 当前待估算的模型消息。
        for (Message message : messages) {
            total += MESSAGE_OVERHEAD + bytes(message.getRole().name());
            // 当前消息的文本内容块。
            for (ContentBlock block : message.getContentBlocks()) {
                if (block instanceof TextContentBlock text) {
                    total += bytes(text.getText());
                } else if (block instanceof MediaContentBlock media) {
                    total += MESSAGE_OVERHEAD + bytes(media.getMimeType()) + bytes(media.getName())
                            + (media.getBytes() == null ? bytes(media.getUrl().toString()) + 1024
                            : (long) media.getBytes().length * 2L);
                } else {
                    throw new IllegalArgumentException("上下文包含未知内容块");
                }
            }
            // 当前助手发出的工具调用。
            for (ToolCall call : message.getToolCalls()) {
                total += MESSAGE_OVERHEAD + bytes(call.getCallId()) + bytes(call.getName())
                        + bytes(call.getArgumentsJson());
            }
            // 当前工具执行结果。
            for (ToolResult result : message.getToolResults()) {
                total += MESSAGE_OVERHEAD + bytes(result.getCallId()) + bytes(result.getContent());
            }
            // 当前协议元数据。
            for (Map.Entry<String, String> entry : message.getProviderMetadata().entrySet()) {
                total += bytes(entry.getKey()) + bytes(entry.getValue());
            }
        }
        // 当前模型可见的工具定义。
        for (ModelToolDefinition tool : tools) {
            total += MESSAGE_OVERHEAD + bytes(tool.getName()) + bytes(tool.getDescription())
                    + bytes(tool.getParameterSchemaJson());
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /**
     * 返回文本所占 UTF-8 字节数。
     *
     * @param text 待估算文本
     * @return 字节数，null 为零
     */
    private static int bytes(String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }
}
