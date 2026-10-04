package com.dingbang.myworld.common.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.dingbang.myworld.common.utils.log.serializer.LogExcludeSerializerModifier;
import com.dingbang.myworld.common.utils.sensitive.serializer.SensitiveJsonSerializerModifier;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * JSON 序列化与反序列化工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Slf4j
public class JsonUtils {
    /**
     * 默认 JSON 对象映射器。
     */
    private static ObjectMapper mapper;

    /**
     * 返回 JSON 转换器共用的映射器。
     *
     * @return 已初始化的对象映射器
     */
    static ObjectMapper mapper() {
        return mapper;
    }

    /**
     * 不序列话null等。降低序列化大小.存储日志等场景使用
     */
    private static ObjectMapper shortMapper;

    /**
     * 敏感字段脱敏使用的对象映射器。
     */
    private final static ObjectMapper SENSITIVE_MAPPER;

    /**
     * 日志序列化：{@code @Sensitive} + {@code @LogExclude}
     */
    private final static ObjectMapper LOG_MAPPER;

    /**
     * 设置一些通用的属性
     */
    static {
        mapper = new ObjectMapper();

        // 如果存在未知属性，则忽略不报错
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 允许key没有双引号
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        // 允许key有单引号
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        // 允许整数以0开头
        mapper.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
        // 允许字符串中存在回车换行控制符
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        //增加LocalDateTime的序列化和反序列化规则
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(LocalDateTime.class, LocalDateTimeSerializer.INSTANCE);
        simpleModule.addDeserializer(LocalDateTime.class, LocalDateTimeDeserializer.INSTANCE);

        mapper.registerModule(simpleModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.registerModule(new JavaTimeModule());

        //简单序列化
        shortMapper = mapper.copy();
        shortMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        SENSITIVE_MAPPER = shortMapper.copy();
        SensitiveJsonSerializerModifier sensitiveJsonSerializerModifier = new SensitiveJsonSerializerModifier();
        SerializerFactory serializerFactory = SENSITIVE_MAPPER.getSerializerFactory().withSerializerModifier(sensitiveJsonSerializerModifier);
        SENSITIVE_MAPPER.setSerializerFactory(serializerFactory);

        LOG_MAPPER = shortMapper.copy();
        LOG_MAPPER.setSerializerFactory(
                LOG_MAPPER.getSerializerFactory().withSerializerModifier(new LogExcludeSerializerModifier()));

    }

    public static String toJSONString(Object obj) {
        return obj != null ? toJSONString(obj, () -> "") : "";
    }

    @SneakyThrows
    public static String toJSONString(Object obj, Supplier<String> defaultSupplier) {
        try {
            return obj != null ? mapper.writeValueAsString(obj) : defaultSupplier.get();
        } catch (Throwable e) {
            throw e;
        }
    }

    @SneakyThrows
    public static String toShortJSONString(Object obj) {
        try {
            return obj != null ? shortMapper.writeValueAsString(obj) : null;
        } catch (Throwable e) {
            throw e;
        }
    }

    @SneakyThrows
    public static String toSensitiveJSONString(Object obj) {
        try {
            return obj != null ? SENSITIVE_MAPPER.writeValueAsString(obj) : null;
        } catch (Throwable e) {
            throw e;
        }
    }

    /**
     * 日志专用序列化：Sensitive + LogExclude
     */
    @SneakyThrows
    public static String toLogJSONString(Object obj) {
        try {
            return obj != null ? LOG_MAPPER.writeValueAsString(obj) : null;
        } catch (Throwable e) {
            throw e;
        }
    }

    @SneakyThrows
    public static String toJSONString(Object obj,boolean prettyFormat) {
        if (obj instanceof String) {
            return (String) obj;
        }
        if (prettyFormat) {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        }else{
            return mapper.writeValueAsString(obj);
        }
    }

    /**
     * 解析复杂泛型
     *
     * @param value         解析数据
     * @param typeReference 泛型
     * @param <T>
     * @return 结果
     */
    @SneakyThrows
    public static <T> T parseObject(String value, TypeReference<T> typeReference) {
        if (StringUtils.isEmpty(value) || typeReference == null) {
            return null;
        }
        try {
            return (typeReference.getType().equals(String.class) ? (T) value : mapper.readValue(value, typeReference));
        } catch (IOException e) {
            throw e;
        }
    }

    public static <T> T parseObject(String value, Class<T> tClass) {
        return StringUtils.isNotBlank(value) ? parseObject(value, tClass, () -> null) : null;
    }

    public static <T> T parseObject(Object obj, Class<T> tClass) {
        return obj != null ? parseObject(toJSONString(obj), tClass, () -> null) : null;
    }

    @SneakyThrows
    public static <T> T parseObject(String value, Class<T> tClass, Supplier<T> defaultSupplier) {
        try {
            if (StringUtils.isBlank(value)) {
                return defaultSupplier.get();
            }
            return mapper.readValue(value, tClass);
        } catch (Throwable e) {
            throw e;
        }
    }


    public static <T> T toJavaObject(String value, Class<T> tClass) {
        return StringUtils.isNotBlank(value) ? toJavaObject(value, tClass, () -> null) : null;
    }

    public static <T> T toJavaObject(Object obj, Class<T> tClass) {
        return obj != null ? toJavaObject(toJSONString(obj), tClass, () -> null) : null;
    }

    @SneakyThrows
    public static <T> T toJavaObject(String value, Class<T> tClass, Supplier<T> defaultSupplier) {
        try {
            if (StringUtils.isBlank(value)) {
                return defaultSupplier.get();
            }
            return mapper.readValue(value, tClass);
        } catch (Throwable e) {
            throw e;
        }
    }

    public static <T> List<T> toJavaObjectList(String value, Class<T> tClass) {
        return StringUtils.isNotBlank(value) ? toJavaObjectList(value, tClass, () -> null) : null;
    }

    public static <T> List<T> parseArray(String value, Class<T> tClass) {
        return StringUtils.isNotBlank(value) ? toJavaObjectList(value, tClass, () -> null) : null;
    }

    public static <T> List<T> toJavaObjectList(Object obj, Class<T> tClass) {
        return obj != null ? toJavaObjectList(toJSONString(obj), tClass, () -> null) : null;
    }

    @SneakyThrows
    public static <T> List<T> toJavaObjectList(String value, Class<T> tClass, Supplier<List<T>> defaultSupplier) {
        try {
            if (StringUtils.isBlank(value)) {
                return defaultSupplier.get();
            }
            JavaType javaType = mapper.getTypeFactory().constructParametricType(List.class, tClass);
            return mapper.readValue(value, javaType);
        } catch (Throwable e) {
            throw e;
        }
    }

    /**
     * json 深度copy
     * @param obj
     * @param tClass
     * @param <T>
     * @return
     */
    public static <T> T jsonCopy(Object obj, Class<T> tClass) {
        return obj != null ? toJavaObject(toJSONString(obj), tClass) : null;
    }

    /**
     * 转化为Map
     * @param value
     * @return
     */
    public static Map<String, Object> toMap(String value) {
        return StringUtils.isNotBlank(value) ? toMap(value, () -> null) : null;
    }

    /**
     * 增加fastjson适配
     *
     * @param value
     * @return
     */
    public static Object parse(String value) {
        return StringUtils.isNotBlank(value) ? toObject(value, () -> null) : null;
    }

    public static Object parse(Object value) {
        return null == value ? toObject(value, () -> null) : null;
    }

    public static Map<String, Object> toMap(Object value) {
        return value != null ? toMap(value, () -> null) : null;
    }

    public static Map<String, Object> toMap(Object value, Supplier<Map<String, Object>> defaultSupplier) {
        if (value == null) {
            return defaultSupplier.get();
        }
        try {
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
        } catch (Exception e) {
            throw e;
        }
        return toMap(toJSONString(value), defaultSupplier);
    }

    public static Map<String, Object> toMap(String value, Supplier<Map<String, Object>> defaultSupplier) {
        if (StringUtils.isBlank(value)) {
            return defaultSupplier.get();
        }
        try {
            return toJavaObject(value, LinkedHashMap.class);
        } catch (Exception e) {
            throw e;
        }
        //return defaultSupplier.get();
    }

    private static Object toObject(String value, Supplier<Map<String, Object>> defaultSupplier) {
        if (StringUtils.isBlank(value)) {
            return defaultSupplier.get();
        }
        try {
            return toJavaObject(value, Object.class);
        } catch (Exception e) {
            throw e;
        }
        //return defaultSupplier.get();
    }

    private static Object toObject(Object value, Supplier<Map<String, Object>> defaultSupplier) {
        if (value == null) {
            return defaultSupplier.get();
        }
        try {
            return toJavaObject(value, Object.class);
        } catch (Exception e) {
            throw e;
        }
        //return defaultSupplier.get();
    }


    public static List<Object> toList(String value) {
        return StringUtils.isNotBlank(value) ? toList(value, () -> null) : null;
    }

    public static List<Object> toList(Object value) {
        return value != null ? toList(value, () -> null) : null;
    }

    public static List<Object> toList(String value, Supplier<List<Object>> defaultSuppler) {
        if (StringUtils.isBlank(value)) {
            return defaultSuppler.get();
        }
        try {
            return toJavaObject(value, List.class);
        } catch (Exception e) {
            throw e;
        }
        //return defaultSuppler.get();
    }

    public static List<Object> toList(Object value, Supplier<List<Object>> defaultSuppler) {
        if (value == null) {
            return defaultSuppler.get();
        }
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return toList(toJSONString(value), defaultSuppler);
    }




}
