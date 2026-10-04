package com.dingbang.myworld.agent.memory;

import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.aiframework.api.ModelToolDefinition;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按摘要覆盖位置组装上下文并保守估算模型输入大小。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class ContextAssembler {
    /** 可替换的输入大小估算器。 */
    private final TokenEstimator estimator;
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
     * 使用字节估算和供应商用量校准。
     */
    public ContextAssembler() {
        this(new CalibratedTokenEstimator());
    }

    /**
     * 注入供应商专用或自定义估算器。
     *
     * @param estimator 模型输入大小估算器
     */
    public ContextAssembler(TokenEstimator estimator) {
        this.estimator = Objects.requireNonNull(estimator, "输入估算器不能为空");
    }

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
     * 计算本次应摘要到的历史边界，剩余部分保留完整交换。
     *
     * @param snapshot 完整历史快照
     * @param policy 可信上下文策略
     * @return 摘要终点的消息索引，不拆分任何用户交换
     */
    public int summaryEnd(SessionSnapshot snapshot, ContextPolicy policy) {
        return summaryEnd(snapshot, policy, policy.getRecentHistoryTokens());
    }

    /**
     * 结合本轮输入的剩余容量选择最近完整交换窗口。
     *
     * @param snapshot 完整历史快照
     * @param policy 可信上下文策略
     * @param availableRecentTokens 当前模型请求可留给近期历史的估算容量
     * @return 摘要终点的消息索引
     */
    public int summaryEnd(SessionSnapshot snapshot, ContextPolicy policy, int availableRecentTokens) {
        /** 已有摘要覆盖的终点。 */
        int covered = snapshot.getSummary() == null ? 0 : snapshot.getSummary().getCoveredMessageCount();
        /** 配置关闭最近窗口时全部未覆盖历史都可进入摘要。 */
        if (policy.getRecentHistoryRounds() == 0) return snapshot.getMessages().size();
        /** 当前准备保留的历史起点。 */
        int retainedStart = snapshot.getMessages().size();
        /** 已纳入窗口的完整交换数量。 */
        int retainedRounds = 0;
        /** 从后向前仅在用户消息边界选择完整交换。 */
        for (int index = snapshot.getMessages().size() - 1; index >= covered; index--) {
            if (snapshot.getMessages().get(index).getRole() != Role.USER) continue;
            if (retainedRounds >= policy.getRecentHistoryRounds()
                    || estimate(snapshot.getMessages().subList(index, snapshot.getMessages().size()), List.of())
                    > Math.min(policy.getRecentHistoryTokens(), Math.max(0, availableRecentTokens))) break;
            retainedStart = index;
            retainedRounds++;
        }
        return retainedStart;
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
        return estimator.estimate(messages, tools);
    }

    /**
     * 采纳供应商返回的单次真实输入用量，供后续调用校准。
     *
     * @param estimatedTokens 本次请求发出前的估算值
     * @param promptTokens 供应商报告的输入 token 数
     */
    public void observe(int estimatedTokens, long promptTokens) {
        estimator.observe(estimatedTokens, promptTokens);
    }
}
