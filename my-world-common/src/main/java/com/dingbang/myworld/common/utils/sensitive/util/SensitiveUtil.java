package com.dingbang.myworld.common.utils.sensitive.util;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.fasterxml.jackson.databind.BeanProperty;
import com.dingbang.myworld.common.utils.sensitive.Sensitive;
import com.dingbang.myworld.common.utils.collection.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敏感数据处理工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class SensitiveUtil {


    private static final Map<Class<?>, Set<String>> SENSITIVE_CLASS_FIELDS_MAP = new ConcurrentHashMap<>(16);
    private static final Logger log = LoggerFactory.getLogger(SensitiveUtil.class);


    public static boolean isMatchField(Field field) {
        Sensitive annotation = field.getAnnotation(Sensitive.class);
        if (annotation != null) {
            return true;
        }
        Class<?> clazz = field.getDeclaringClass();
        return isMatched(clazz, field.getName());
    }


    public static boolean isMatchField(BeanProperty property) {
        if(property == null) {
            return false;
        }
        Sensitive sensitive = property.getAnnotation(Sensitive.class);
        if (sensitive != null) {
            return true;
        }
        Class<?> clazz = property.getMember().getDeclaringClass();
        return isMatched(clazz, property.getName());
    }


    private static boolean isMatched(Class<?> clazz, String fieldName) {
        if(SENSITIVE_CLASS_FIELDS_MAP.containsKey(clazz)) {
            Set<String> values = SENSITIVE_CLASS_FIELDS_MAP.get(clazz);
            return values.contains(fieldName);
        }
        for (Map.Entry<Class<?>, Set<String>> entry : SENSITIVE_CLASS_FIELDS_MAP.entrySet()) {
            if(clazz.isAssignableFrom(entry.getKey())){
                if(entry.getValue().contains(fieldName)) {
                    return true;
                }
            }
        }
        return false;

    }


    public static void registerSensitiveClassField(Class<?> clazz, Field field) {
        registerSensitiveClassField(clazz, Collections.singletonList(field.getName()));
    }

    public static void registerSensitiveClassField(Class<?> clazz, List<String> fieldNames) {
        Set<String> exists = MapUtils.computeIfAbsent(SENSITIVE_CLASS_FIELDS_MAP, clazz, x -> new ConcurrentHashSet<>(), true);
        exists.addAll(fieldNames);
        SENSITIVE_CLASS_FIELDS_MAP.put(clazz, exists);
    }

    public static void registerSensitiveClassField(String className, List<String> fieldNames) {
        try {
            Class<?> clazz = Class.forName(className);
            registerSensitiveClassField(clazz, fieldNames);
        } catch (Throwable e) {
            log.info("[registerSensitiveClassField]隐私字段注册失败: {} {} ",className, e.getMessage());
        }
    }



}
