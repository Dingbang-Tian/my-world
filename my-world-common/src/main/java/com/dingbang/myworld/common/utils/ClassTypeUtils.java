package com.dingbang.myworld.common.utils;

import com.google.common.primitives.Primitives;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.Map;

/**
 * Java 类型判断工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClassTypeUtils {


    /**
     * 可直接处理的基础类型集合。
     */
    private static final Class<?>[] BASE_CLASSES = new Class<?>[]{Void.class, Object.class, Class.class, String.class};



    /**
     * 是否为一个简单的java Bean的类型
     * @param clazz
     * @return
     */
    public static boolean isSimpleType(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        if (isBaseType(clazz)) {
            return false;
        }
        if (clazz.isInterface()) {
            return false;
        }
        if (isArray(clazz)) {
            return false;
        }
        if (isCollection(clazz)) {
            return false;
        }
        if (isMap(clazz)) {
            return false;
        }
        if (clazz.isAnnotation()) {
            return false;
        }
        if (clazz.isSynthetic()) {
            return false;
        }
        return true;

    }


    /**
     * java常见的内置基本类型
     * @param clazz
     * @return
     */
    public static boolean isBaseType(Class<?> clazz) {
        if(clazz.isPrimitive()) {
            return true;
        }
        if(Primitives.isWrapperType(clazz)){
            return true;
        }
        for (Class<?> baseClass : BASE_CLASSES) {
            if(clazz == baseClass) {
                return true;
            }
        }
        return false;
    }


    /**
     * 是否为枚举
     * @param clazz
     * @return
     */
    public static boolean isEnum(final Class<?> clazz) {
        return clazz.isEnum();
    }

    /**
     * 是否为 数组
     *
     * @param clazz
     * @return
     */
    public static boolean isArray(final Class<?> clazz) {
        return clazz.isArray();
    }

    public static boolean isCollection(final Class<?> clazz) {
        return Collection.class.isAssignableFrom(clazz);
    }

    public static boolean isMap(final Class<?> clazz) {
        return Map.class.isAssignableFrom(clazz);
    }

}
