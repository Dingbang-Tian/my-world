package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

import java.util.Objects;

/**
 * 单次模型请求中可见的工具名称、用途与参数结构。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class ModelToolDefinition {
    /** 工具名称。 */
    private final String name;
    /** 工具用途。 */
    private final String description;
    /** 参数的 JSON Schema 文本。 */
    private final String parameterSchemaJson;

    /**
     * 固定模型可见的工具说明。
     *
     * @param name 工具名称
     * @param description 工具用途
     * @param parameterSchemaJson 参数 JSON Schema
     */
    public ModelToolDefinition(String name, String description, String parameterSchemaJson) {
        if (StringUtils.isBlank(name) || StringUtils.isBlank(description)
                || StringUtils.isBlank(parameterSchemaJson)) {
            throw new IllegalArgumentException("模型工具名称、用途和参数结构不能为空");
        }
        this.name = name;
        this.description = description;
        this.parameterSchemaJson = Objects.requireNonNull(parameterSchemaJson);
    }
}
