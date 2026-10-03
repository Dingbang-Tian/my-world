package com.dingbang.myworld.agent.session;

import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;

import java.util.List;

/**
 * 单会话的归属校验、独占运行和版本化消息提交契约。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public interface Session {

    /**
     * 校验调用方归属。
     *
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     */
    void requireOwner(String ownerId, String appId, String agentId);

    /**
     * 尝试取得独占运行权。
     *
     * @return 成功取得时为 true
     */
    boolean tryStart();

    /**
     * 释放独占运行权。
     */
    void release();

    /**
     * 读取完整历史和版本的同一快照。
     *
     * @return 不可变快照
     */
    SessionSnapshot snapshot();

    /**
     * 原子提交一次完整交换。
     *
     * @param expectedVersion 读取历史时的版本
     * @param messages 完整的用户、助手及工具消息
     */
    void appendExchange(long expectedVersion, List<Message> messages);

    /**
     * 返回会话级模型选项。
     *
     * @return 不可变选项
     */
    ModelOptions options();

    /**
     * 表示当前会话是否正在执行。
     *
     * @return 正在执行时为 true
     */
    boolean isBusy();
}
