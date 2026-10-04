package com.dingbang.myworld.agent.memory;

import com.dingbang.myworld.aiframework.api.ModelToolDefinition;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.content.ContentBlock;
import com.dingbang.myworld.aiframework.model.content.MediaContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 按 UTF-8 字节和协议开销估算模型输入大小。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
public final class ByteTokenEstimator implements TokenEstimator {
    /** 每条消息的固定协议开销估算值。 */
    private static final int MESSAGE_OVERHEAD = 16;

    /**
     * 按消息、工具参数和工具定义的字节数估算输入。
     *
     * @param messages 模型消息
     * @param tools 模型工具定义
     * @return 估算值
     */
    @Override
    public int estimate(List<Message> messages, List<ModelToolDefinition> tools) {
        /** 消息和协议开销累计值。 */
        long total = 0;
        /** 当前待估算的消息。 */
        for (Message message : messages) {
            total += MESSAGE_OVERHEAD + bytes(message.getRole().name());
            /** 当前内容块。 */
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
            /** 当前工具调用。 */
            for (ToolCall call : message.getToolCalls()) {
                total += MESSAGE_OVERHEAD + bytes(call.getCallId()) + bytes(call.getName())
                        + bytes(call.getArgumentsJson());
            }
            /** 当前工具结果。 */
            for (ToolResult result : message.getToolResults()) {
                total += MESSAGE_OVERHEAD + bytes(result.getCallId()) + bytes(result.getContent());
            }
            /** 当前供应商元数据。 */
            for (Map.Entry<String, String> entry : message.getProviderMetadata().entrySet()) {
                total += bytes(entry.getKey()) + bytes(entry.getValue());
            }
        }
        /** 当前模型可见工具。 */
        for (ModelToolDefinition tool : tools) {
            total += MESSAGE_OVERHEAD + bytes(tool.getName()) + bytes(tool.getDescription())
                    + bytes(tool.getParameterSchemaJson());
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /**
     * 返回字符串占用的 UTF-8 字节数。
     *
     * @param value 待估算文本
     * @return 字节数，空值为零
     */
    private static int bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
