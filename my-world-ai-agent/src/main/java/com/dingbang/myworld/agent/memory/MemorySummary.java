package com.dingbang.myworld.agent.memory;

import lombok.Data;

/**
 * 保存有损摘要及其覆盖的完整历史消息数量。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class MemorySummary {
    /** 摘要文本。 */
    private final String text;
    /** 从历史开头起已覆盖的消息数量，必须落在交换边界。 */
    private final int coveredMessageCount;

    /**
     * 固定摘要和覆盖位置。
     *
     * @param text 非空摘要文本
     * @param coveredMessageCount 已覆盖消息数量
     */
    public MemorySummary(String text, int coveredMessageCount) {
        if (text == null || text.isBlank() || coveredMessageCount < 1) {
            throw new IllegalArgumentException("摘要内容或覆盖位置无效");
        }
        this.text = text;
        this.coveredMessageCount = coveredMessageCount;
    }
}
