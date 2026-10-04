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
