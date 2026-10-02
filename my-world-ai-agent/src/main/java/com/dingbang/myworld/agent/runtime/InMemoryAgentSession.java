package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.aiframework.model.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在进程内保存单个 Agent 会话的完整用户与助手交换。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
final class InMemoryAgentSession {

    /**
     * 会话所属应用。
     */
    private final String appId;

    /**
     * 会话所属 Agent。
     */
    private final String agentId;

    /**
     * 已完成交换的原始消息顺序。
     */
    private final List<Message> history = new ArrayList<>();

    /**
     * 当前会话是否已有运行在执行。
     */
    private final AtomicBoolean busy = new AtomicBoolean();

    /**
     * 创建归属固定的进程内会话。
     *
     * @param appId 所属应用
     * @param agentId 所属 Agent
     */
    InMemoryAgentSession(String appId, String agentId) {
        this.appId = Objects.requireNonNull(appId, "应用标识不能为 null");
        this.agentId = Objects.requireNonNull(agentId, "Agent 标识不能为 null");
    }

    /**
     * 检查调用方是否属于此会话的应用和 Agent。
     *
     * @param expectedAppId 调用方应用标识
     * @param expectedAgentId 调用方 Agent 标识
     * @throws IllegalArgumentException 会话归属不匹配时
     */
    void requireOwner(String expectedAppId, String expectedAgentId) {
        if (!appId.equals(expectedAppId) || !agentId.equals(expectedAgentId)) {
            throw new IllegalArgumentException("会话不属于指定的应用和 Agent");
        }
    }

    /**
     * 尝试取得当前会话的唯一运行权。
     *
     * @return 已取得运行权时为 true
     */
    boolean tryStart() {
        // 只有 busy 仍为 false 的线程能把它改为 true，因此两个运行不会同时占用同一会话。
        return busy.compareAndSet(false, true);
    }

    /**
     * 释放当前运行占用的会话。
     */
    void release() {
        // DefaultAgentRun.finish 在成功、失败两种终态都会调用这里，允许下一轮继续执行。
        busy.set(false);
    }

    /**
     * 复制已完成交换，供下一次模型请求使用。
     *
     * @return 不可修改的历史消息快照
     */
    synchronized List<Message> getHistorySnapshot() {
        // 返回副本而不是 history 本身，模型请求构造期间不会修改会话中的原始历史。
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /**
     * 按顺序记录成功完成的一次用户与助手交换。
     *
     * @param user 本轮用户消息
     * @param assistant 本轮完整助手消息
     */
    synchronized void appendExchange(Message user, Message assistant) {
        appendExchange(java.util.Arrays.asList(user, assistant));
    }

    /**
     * 按原始顺序记录成功运行产生的用户、助手和工具消息。
     *
     * @param messages 本次运行生成的完整消息
     */
    synchronized void appendExchange(List<Message> messages) {
        Objects.requireNonNull(messages, "消息列表不能为 null").forEach(Objects::requireNonNull);
        history.addAll(messages);
    }
}
