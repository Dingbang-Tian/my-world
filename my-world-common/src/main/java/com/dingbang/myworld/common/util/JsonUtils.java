package com.dingbang.myworld.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * JSON序列化工具
 *
 * @author dingbang.tian
 * @since 2026/09/21
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonUtils {

    /**
     * JSON处理器
     */
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /**
     * 将对象序列化为JSON
     *
     * @param value
     * @return json
     */
    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON serialization failed", exception);
        }
    }

    /**
     * 将JSON反序列化为指定类型
     *
     * @param json
     * @param type
     * @return value
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON deserialization failed", exception);
        }
    }

    /**
     * 将JSON反序列化为泛型类型
     *
     * @param json
     * @param type
     * @return value
     */
    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON deserialization failed", exception);
        }
    }

    /**
     * 转换对象类型
     *
     * @param source
     * @param targetType
     * @return value
     */
    public static <T> T convert(Object source, Class<T> targetType) {
        return OBJECT_MAPPER.convertValue(source, targetType);
    }
}
