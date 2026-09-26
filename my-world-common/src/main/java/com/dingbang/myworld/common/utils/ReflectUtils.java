package com.dingbang.myworld.common.utils;

import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 反射操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class ReflectUtils {

    private static final Field[] NO_FIELDS = {};

    /**
     * Cache for {@link Class#getDeclaredFields()}, allowing for fast iteration.
     */
    private static final Map<Class<?>, Field[]> DECLARED_FIELDS_CACHE = new ConcurrentReferenceHashMap<>(256);

    public static void doWithFields(Class<?> clazz, Consumer<Field> consumer) {
        // Keep backing up the inheritance hierarchy.
        Class<?> targetClass = clazz;
        do {
            Field[] fields = getDeclaredFields(targetClass);
            for (Field field : fields) {
                consumer.accept(field);
            }
            targetClass = targetClass.getSuperclass();
        }
        while (targetClass != null && targetClass != Object.class);
    }

    private static Field[] getDeclaredFields(Class<?> clazz) {
        Optional.ofNullable(clazz).orElseThrow(() -> new IllegalArgumentException("Class must not be null"));
        Field[] result = DECLARED_FIELDS_CACHE.get(clazz);
        if (result == null) {
            try {
                result = clazz.getDeclaredFields();
                DECLARED_FIELDS_CACHE.put(clazz, (result.length == 0 ? NO_FIELDS : result));
            } catch (Throwable ex) {
                throw new IllegalStateException("Failed to introspect Class [" + clazz.getName() +
                        "] from ClassLoader [" + clazz.getClassLoader() + "]", ex);
            }
        }
        return result;
    }

    public static <T> Object getFieldValue(T t, String fieldName) {
        Class<?> aClass = t.getClass();
        do {
            try {
                Field field = aClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(t);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                aClass = aClass.getSuperclass();
            }
        } while (aClass != null);
        return null;
    }

    private static final Set<Class<?>> BOX_TYPE_MAP = new HashSet<>();

    static {
        BOX_TYPE_MAP.add(Integer.class);
        BOX_TYPE_MAP.add(Short.class);
        BOX_TYPE_MAP.add(Float.class);
        BOX_TYPE_MAP.add(Character.class);
        BOX_TYPE_MAP.add(Double.class);
        BOX_TYPE_MAP.add(Long.class);
        BOX_TYPE_MAP.add(Byte.class);
        BOX_TYPE_MAP.add(String.class);
        BOX_TYPE_MAP.add(int.class);
        BOX_TYPE_MAP.add(short.class);
        BOX_TYPE_MAP.add(float.class);
        BOX_TYPE_MAP.add(char.class);
        BOX_TYPE_MAP.add(double.class);
        BOX_TYPE_MAP.add(long.class);
        BOX_TYPE_MAP.add(byte.class);
    }

    public static boolean isBoxType(Class<?> clazz) {
        return BOX_TYPE_MAP.contains(clazz);
    }
}
