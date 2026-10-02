package com.dingbang.myworld.agent.prompt;

import java.util.Set;

/**
 * 按模板标识提供已加载的不可变提示词快照。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public interface PromptRepository {

    /**
     * 获取模板快照。
     *
     * @param templateId 模板标识
     * @return 已加载的模板快照
     * @throws IllegalArgumentException 模板不存在或已禁用时
     */
    PromptTemplateSnapshot get(String templateId);

    /**
     * 获取当前启用的模板标识。
     *
     * @return 不可修改的模板标识集合
     */
    Set<String> keys();
}
