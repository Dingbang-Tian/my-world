package com.dingbang.myworld.agent.api;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一个应用内 Agent 的可信身份、模型和系统模板定义。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class AgentDefinition {

    /**
     * 所属应用标识。
     */
    private final String appId;

    /**
     * Agent 标识。
     */
    private final String agentId;

    /**
     * 显示名称。
     */
    private final String name;

    /**
     * Agent 职责描述。
     */
    private final String description;

    /**
     * 已配置的模型标识。
     */
    private final String modelId;

    /**
     * 系统模板标识。
     */
    private final String promptTemplateId;

    /**
     * 直接授权的工具名称。
     */
    private final List<String> toolIds;

    /**
     * 启用的技能标识。
     */
    private final List<String> skillIds;

    /**
     * 一次运行允许的最大模型回合数。
     */
    private final int maxModelTurns;

    /** 整次运行的可信预算。 */
    private final AgentLimits limits;

    /**
     * 创建不可变定义；省略模板标识时使用公共系统模板。
     *
     * @param appId 所属应用标识
     * @param agentId Agent 标识
     * @param name 显示名称
     * @param description 职责描述，可为空字符串
     * @param modelId 已配置模型标识
     * @param promptTemplateId 系统模板标识，null 表示公共系统模板
     * @throws IllegalArgumentException 必填标识或名称为空时
     * @throws NullPointerException 描述为 null 时
     */
    public AgentDefinition(String appId, String agentId, String name, String description,
                           String modelId, String promptTemplateId) {
        this(appId, agentId, name, description, modelId, promptTemplateId,
                Collections.emptyList(), Collections.emptyList(), 8);
    }

    /**
     * 创建包含工具、技能和模型回合限制的可信定义。
     *
     * @param appId 所属应用标识
     * @param agentId Agent 标识
     * @param name 显示名称
     * @param description 职责描述
     * @param modelId 模型标识
     * @param promptTemplateId 系统模板标识，可为 null
     * @param toolIds 直接授权的工具名称
     * @param skillIds 启用的技能标识
     * @param maxModelTurns 最大模型回合数，必须大于零
     */
    public AgentDefinition(String appId, String agentId, String name, String description,
                           String modelId, String promptTemplateId, List<String> toolIds,
                           List<String> skillIds, int maxModelTurns) {
        this(appId, agentId, name, description, modelId, promptTemplateId, toolIds, skillIds,
                AgentLimits.defaults(maxModelTurns));
    }

    /**
     * 创建具有完整运行预算的可信定义。
     *
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param name 显示名称
     * @param description 职责描述
     * @param modelId 模型标识
     * @param promptTemplateId 模板标识，可为空
     * @param toolIds 授权工具
     * @param skillIds 启用技能
     * @param limits 整次运行预算
     */
    public AgentDefinition(String appId, String agentId, String name, String description,
                           String modelId, String promptTemplateId, List<String> toolIds,
                           List<String> skillIds, AgentLimits limits) {
        if (StringUtils.isBlank(appId) || StringUtils.isBlank(agentId)
                || StringUtils.isBlank(name) || StringUtils.isBlank(modelId)) {
            throw new IllegalArgumentException("应用、Agent、名称和模型标识不能为空");
        }
        if (promptTemplateId != null && StringUtils.isBlank(promptTemplateId)) {
            throw new IllegalArgumentException("模板标识不能为空白字符串");
        }
        this.appId = appId;
        this.agentId = agentId;
        this.name = name;
        this.description = Objects.requireNonNull(description, "Agent 描述不能为 null");
        this.modelId = modelId;
        this.promptTemplateId = promptTemplateId == null ? "agent/system" : promptTemplateId;
        this.limits = Objects.requireNonNull(limits, "运行预算不能为 null");
        Objects.requireNonNull(toolIds, "工具标识不能为 null").forEach(Objects::requireNonNull);
        Objects.requireNonNull(skillIds, "技能标识不能为 null").forEach(Objects::requireNonNull);
        this.toolIds = Collections.unmodifiableList(new ArrayList<>(toolIds));
        this.skillIds = Collections.unmodifiableList(new ArrayList<>(skillIds));
        this.maxModelTurns = limits.getMaxModelTurns();
    }
}
