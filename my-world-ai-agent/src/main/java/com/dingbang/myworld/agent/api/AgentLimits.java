package com.dingbang.myworld.agent.api;

import lombok.Data;
import java.time.Duration;
import java.util.Objects;

/**
 * 由可信 Agent 定义提供的整次运行预算。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Data
public final class AgentLimits {
    /**
     * 包含最终回答在内的最大模型回合数。
     */
    private final int maxModelTurns;
    /**
     * 最多尝试的工具调用数，参数失败也计入。
     */
    private final int maxToolCalls;
    /**
     * 整次运行的输出字符上限，按 UTF-16 计数，包含模型与工具输出。
     */
    private final int maxOutputCharacters;
    /**
     * 历史回放及单个消费者待处理队列的事件容量。
     */
    private final int eventBufferCapacity;
    /**
     * 从 execute 开始计算的全局运行时限。
     */
    private final Duration timeout;
    /**
     * 单个计划允许的最大步骤数。
     */
    private final int maxPlanSteps;
    /**
     * 子 Agent 的最大嵌套层数，根运行为零层。
     */
    private final int maxSubAgentDepth;

    /**
     * 创建不可变预算。
     *
     * @param maxModelTurns 最大模型回合数，至少一轮
     * @param maxToolCalls 最大工具次数，允许为零
     * @param maxOutputCharacters 最大输出字符数，必须大于零
     * @param eventBufferCapacity 有限事件容量，必须大于零
     * @param timeout 全局时限，至少一毫秒且最多一天
     */
    public AgentLimits(int maxModelTurns, int maxToolCalls, int maxOutputCharacters,
                       int eventBufferCapacity, Duration timeout) {
        this(maxModelTurns, maxToolCalls, maxOutputCharacters, eventBufferCapacity, timeout, 6);
    }

    /**
     * 创建包含计划步骤上限的不可变预算。
     *
     * @param maxModelTurns 最大模型回合数
     * @param maxToolCalls 最大工具调用数
     * @param maxOutputCharacters 最大输出字符数
     * @param eventBufferCapacity 事件缓存容量
     * @param timeout 全局时限
     * @param maxPlanSteps 单个计划最大步骤数
     */
    public AgentLimits(int maxModelTurns, int maxToolCalls, int maxOutputCharacters,
                       int eventBufferCapacity, Duration timeout, int maxPlanSteps) {
        this(maxModelTurns, maxToolCalls, maxOutputCharacters, eventBufferCapacity, timeout,
                maxPlanSteps, 2);
    }

    /**
     * 创建包含计划和子 Agent 深度限制的运行预算。
     *
     * @param maxModelTurns 最大模型回合数
     * @param maxToolCalls 最大工具调用数
     * @param maxOutputCharacters 最大输出字符数
     * @param eventBufferCapacity 事件缓存容量
     * @param timeout 全局时限
     * @param maxPlanSteps 单个计划最大步骤数
     * @param maxSubAgentDepth 子 Agent 最大嵌套层数，可为零
     */
    public AgentLimits(int maxModelTurns, int maxToolCalls, int maxOutputCharacters,
                       int eventBufferCapacity, Duration timeout, int maxPlanSteps,
                       int maxSubAgentDepth) {
        Objects.requireNonNull(timeout, "运行时限不能为 null");
        if (maxModelTurns < 1 || maxToolCalls < 0 || maxOutputCharacters < 1 || eventBufferCapacity < 1
                || maxPlanSteps < 1 || maxSubAgentDepth < 0 || timeout.compareTo(Duration.ofMillis(1)) < 0
                || timeout.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("运行预算无效");
        }
        this.maxModelTurns = maxModelTurns;
        this.maxToolCalls = maxToolCalls;
        this.maxOutputCharacters = maxOutputCharacters;
        this.eventBufferCapacity = eventBufferCapacity;
        this.timeout = timeout;
        this.maxPlanSteps = maxPlanSteps;
        this.maxSubAgentDepth = maxSubAgentDepth;
    }

    /**
     * 保持既有回合配置并使用默认工具、输出、事件与时间预算。
     *
     * @param maxModelTurns 最大模型回合数
     * @return 默认预算
     */
    public static AgentLimits defaults(int maxModelTurns) {
        return new AgentLimits(maxModelTurns, 32, 65536, 512, Duration.ofSeconds(120));
    }
}
