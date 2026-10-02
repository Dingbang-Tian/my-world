package com.dingbang.myworld.agent.api;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

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
    }
}
