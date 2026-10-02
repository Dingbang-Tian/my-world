package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 保存单个受支持参数的类型规则并生成对应 JSON Schema。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Getter
final class ToolParameter {
    /**
     * 参数字段。
     */
    private final Field field;
    /**
     * 参数说明。
     */
    private final String description;
    /**
     * 是否必须出现。
     */
    private final boolean required;
    /**
     * JSON Schema 类型。
     */
    private final String schemaType;
    /**
     * 列表或数组元素类型；普通字段为 null。
     */
    private final Class<?> itemType;

    /**
     * 检查字段可表示为受支持的 JSON Schema 类型。
     *
     * @param field 参数字段
     * @param annotation 参数注解
     */
    ToolParameter(Field field, ToolParam annotation) {
        this.field = field;
        this.description = annotation.description();
        this.required = annotation.required();
        if (field.getType().isArray()) {
            itemType = field.getType().getComponentType();
            schemaType = "array";
            scalarType(itemType);
        } else if (field.getType() == java.util.List.class) {
            // 列表声明的泛型信息。
            Type generic = field.getGenericType();
            if (!(generic instanceof ParameterizedType)
                    || !(((ParameterizedType) generic).getActualTypeArguments()[0] instanceof Class<?>)) {
                throw new IllegalArgumentException("列表参数必须声明具体元素类型: " + field.getName());
            }
            itemType = (Class<?>) ((ParameterizedType) generic).getActualTypeArguments()[0];
            schemaType = "array";
            scalarType(itemType);
        } else {
            itemType = null;
            schemaType = scalarType(field.getType());
        }
    }

    /**
     * 生成字段的模型可见 Schema。
     *
     * @param node 待填充的 Schema 节点
     */
    void writeSchema(ObjectNode node) {
        node.put("type", schemaType);
        node.put("description", description);
        if (itemType != null) {
            // 数组元素的 Schema。
            ObjectNode items = node.putObject("items");
            items.put("type", scalarType(itemType));
            writeEnum(items, itemType);
        } else {
            writeEnum(node, field.getType());
        }
    }

    /**
     * 校验 JSON 字段类型与枚举值。
     *
     * @param value 待校验的 JSON 值
     * @throws IllegalArgumentException 字段类型或枚举值错误时
     */
    void validate(JsonNode value) {
        if (value == null || value.isNull()) {
            if (required) {
                throw new IllegalArgumentException("缺少必填参数: " + field.getName());
            }
            return;
        }
        if (itemType != null) {
            if (!value.isArray()) {
                throw new IllegalArgumentException("参数必须为数组: " + field.getName());
            }
            for (JsonNode element : value) {
                validateScalar(element, itemType);
            }
        } else {
            validateScalar(value, field.getType());
        }
    }

    /**
     * 将受支持的 Java 标量映射为 JSON Schema 类型。
     *
     * @param type Java 类型
     * @return Schema 类型
     * @throws IllegalArgumentException 类型不受支持时
     */
    private static String scalarType(Class<?> type) {
        if (type == String.class || type.isEnum()) {
            return "string";
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == float.class || type == Float.class || type == double.class
                || type == Double.class || type == BigDecimal.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        throw new IllegalArgumentException("不支持的工具参数类型: " + type.getName());
    }

    /**
     * 写入枚举的合法字符串值。
     *
     * @param node Schema 节点
     * @param type Java 类型
     */
    private static void writeEnum(ObjectNode node, Class<?> type) {
        if (type.isEnum()) {
            // 枚举可选值。
            ArrayNode values = node.putArray("enum");
            for (Object constant : type.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
        }
    }

    /**
     * 校验一个非 null JSON 标量。
     *
     * @param value JSON 值
     * @param type Java 类型
     * @throws IllegalArgumentException 值与 Java 类型不匹配时
     */
    private void validateScalar(JsonNode value, Class<?> type) {
        // 当前 JSON 值与 Java 类型是否一致。
        boolean valid;
        if (type == String.class) {
            valid = value.isTextual();
        } else if (type.isEnum()) {
            valid = value.isTextual();
            if (valid) {
                valid = java.util.Arrays.stream(type.getEnumConstants())
                        .anyMatch(constant -> ((Enum<?>) constant).name().equals(value.textValue()));
            }
        } else if (type == int.class || type == Integer.class) {
            valid = value.isIntegralNumber() && value.canConvertToInt();
        } else if (type == long.class || type == Long.class) {
            valid = value.isIntegralNumber() && value.canConvertToLong();
        } else if (type == float.class || type == Float.class || type == double.class
                || type == Double.class || type == BigDecimal.class) {
            valid = value.isNumber();
        } else {
            valid = value.isBoolean();
        }
        if (!valid) {
            throw new IllegalArgumentException("参数类型或取值无效: " + field.getName());
        }
    }
}
