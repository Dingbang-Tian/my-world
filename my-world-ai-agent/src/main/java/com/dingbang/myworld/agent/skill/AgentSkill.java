package com.dingbang.myworld.agent.skill;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 为 Agent 组合工具和模型可读使用说明的不可变技能。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class AgentSkill {
    /**
     * 技能标识。
     */
    private final String skillId;
    /**
     * 模型可读的使用说明。
     */
    private final String instructions;
    /**
     * 此技能引用的工具名称。
     */
    private final List<String> toolIds;

    /**
     * 固定技能说明与工具引用。
     *
     * @param skillId 技能标识
     * @param instructions 模型可读的使用说明
     * @param toolIds 技能引用的工具名称
     */
    public AgentSkill(String skillId, String instructions, List<String> toolIds) {
        if (StringUtils.isBlank(skillId) || StringUtils.isBlank(instructions)) {
            throw new IllegalArgumentException("技能标识和说明不能为空");
        }
        Objects.requireNonNull(toolIds, "工具名称不能为 null").forEach(Objects::requireNonNull);
        this.skillId = skillId;
        this.instructions = instructions;
        this.toolIds = Collections.unmodifiableList(new ArrayList<>(toolIds));
    }
}
