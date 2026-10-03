package com.dingbang.myworld.agent.session;

import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.agent.memory.MemorySummary;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一次读取取得的会话历史及版本快照。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class SessionSnapshot {
    /** 历史版本。 */
    private final long version;
    /** 已完成交换的消息。 */
    private final List<Message> messages;
    /** 已提交的摘要，尚无摘要时为 null。 */
    private final MemorySummary summary;

    /**
     * 固定历史副本。
     *
     * @param version 历史版本
     * @param messages 已完成交换的消息
     */
    public SessionSnapshot(long version, List<Message> messages) {
        this(version, messages, null);
    }

    /**
     * 固定历史、版本及摘要的同一快照。
     *
     * @param version 历史版本
     * @param messages 完整原始历史
     * @param summary 已提交摘要，可为 null
     */
    public SessionSnapshot(long version, List<Message> messages, MemorySummary summary) {
        this.version = version;
        this.messages = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(messages)));
        this.summary = summary;
    }
}
