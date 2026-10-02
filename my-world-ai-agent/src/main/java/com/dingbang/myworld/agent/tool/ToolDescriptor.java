package com.dingbang.myworld.agent.tool;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;
import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 经注册校验的工具身份、参数类型和模型可见 JSON Schema。
 *
 * @author Sebastian
 * @since 2026/10/02
 * @param <P> 工具参数类型
 */
@Data
public final class ToolDescriptor<P> {
    /**
     * 用于参数 JSON 解析的映射器。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    /**
     * 模型可见的工具名称。
     */
    private final String name;
    /**
     * 模型可见的工具用途。
     */
    private final String description;
    /**
     * 参数 Java 类型。
     */
    private final Class<P> parameterType;
    /**
     * 参数字段规则。
     */
    private final List<ToolParameter> parameters;
    /**
     * 参数 JSON Schema。
     */
    private final ObjectNode parameterSchema;

    /**
     * 由显式名称、说明和参数类建立描述。
     *
     * @param name 工具名称
     * @param description 工具用途
     * @param parameterType 参数类
     * @param <P> 参数类型
     * @return 工具描述
     * @throws IllegalArgumentException 名称、字段或类型不受支持时
     */
    public static <P> ToolDescriptor<P> of(String name, String description, Class<P> parameterType) {
        return new ToolDescriptor<>(name, description, parameterType);
    }

    /**
     * 读取工具类的 ToolInfo 注解与参数类的 ToolParam 注解。
     *
     * @param toolType 工具实现类
     * @param parameterType 参数类
     * @param <P> 参数类型
     * @return 工具描述
     * @throws IllegalArgumentException 工具缺少注解时
     */
    public static <P> ToolDescriptor<P> fromAnnotations(Class<?> toolType, Class<P> parameterType) {
        // 工具类上的身份声明。
        ToolInfo info = toolType.getAnnotation(ToolInfo.class);
        if (info == null) {
            throw new IllegalArgumentException("工具类缺少 @ToolInfo: " + toolType.getName());
        }
        return of(info.name(), info.description(), parameterType);
    }

    /**
     * 校验工具身份和参数类，并冻结 Schema。
     *
     * @param name 工具名称
     * @param description 工具用途
     * @param parameterType 参数类
     */
    private ToolDescriptor(String name, String description, Class<P> parameterType) {
        if (StringUtils.isBlank(name) || !name.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("工具名称必须由字母、数字和下划线组成并以字母开头");
        }
        if (StringUtils.isBlank(description)) {
            throw new IllegalArgumentException("工具说明不能为空");
        }
        this.name = name;
        this.description = description;
        this.parameterType = Objects.requireNonNull(parameterType, "参数类不能为 null");
        if (parameterType.getSuperclass() != Object.class) {
            throw new IllegalArgumentException("工具参数类不能继承其他参数字段: " + parameterType.getName());
        }
        try {
            parameterType.getDeclaredConstructor();
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException("工具参数类需要无参构造方法: " + parameterType.getName(), exception);
        }
        // 已校验的参数字段。
        List<ToolParameter> found = new ArrayList<>();
        // 参数对象的 JSON Schema。
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        // 参数属性 Schema。
        ObjectNode properties = schema.putObject("properties");
        // 必填参数名。
        ArrayNode required = schema.putArray("required");
        schema.put("additionalProperties", false);
        for (Field field : parameterType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (Modifier.isFinal(field.getModifiers())) {
                throw new IllegalArgumentException("工具参数字段不能为 final: " + field.getName());
            }
            // 字段上的模型参数声明。
            ToolParam annotation = field.getAnnotation(ToolParam.class);
            if (annotation == null || StringUtils.isBlank(annotation.description())) {
                throw new IllegalArgumentException("工具参数字段缺少有效 @ToolParam: " + field.getName());
            }
            // 已校验的字段规则。
            ToolParameter parameter = new ToolParameter(field, annotation);
            found.add(parameter);
            parameter.writeSchema(properties.putObject(parameter.getField().getName()));
            if (parameter.isRequired()) {
                required.add(parameter.getField().getName());
            }
        }
        this.parameters = Collections.unmodifiableList(found);
        this.parameterSchema = schema;
    }

    /**
     * 返回独立副本，防止调用方修改注册快照。
     *
     * @return 参数 JSON Schema
     */
    public ObjectNode getParameterSchema() {
        return parameterSchema.deepCopy();
    }

    /**
     * 严格解析参数 JSON，校验必填、类型与未知字段。
     *
     * @param json 模型提供的完整参数 JSON
     * @return Java 参数对象
     * @throws IllegalArgumentException JSON 或参数无效时
     */
    public P parse(String json) {
        try {
            // 模型提交的参数对象。
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("工具参数必须为 JSON 对象");
            }
            root.fieldNames().forEachRemaining(name -> {
                if (parameters.stream().noneMatch(parameter -> parameter.getField().getName().equals(name))) {
                    throw new IllegalArgumentException("未知工具参数: " + name);
                }
            });
            for (ToolParameter parameter : parameters) {
                parameter.validate(root.get(parameter.getField().getName()));
            }
            return MAPPER.treeToValue(root, parameterType);
        } catch (IOException exception) {
            throw new IllegalArgumentException("工具参数不是可解析的 JSON 对象", exception);
        }
    }
}
