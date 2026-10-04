package com.dingbang.myworld.common.utils.lang;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.PropDesc;
import cn.hutool.core.lang.reflect.MethodHandleUtil;
import cn.hutool.core.map.WeakConcurrentMap;
import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.dingbang.myworld.common.utils.bean.LambdaBeanDesc;
import com.dingbang.myworld.common.utils.bean.LambdaPropDesc;
import com.dingbang.myworld.common.utils.collection.MapUtils;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;

import java.io.Serializable;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.invoke.MethodType.methodType;

/**
 * Bean 操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@SuppressWarnings("unchecked")
public class BeanUtils extends BeanUtil {

    /**
     * 深拷贝使用独立 ObjectMapper，避免走到日志脱敏序列化
     */
    private static final ObjectMapper COPY_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    /**
     * 按类缓存的 Bean 属性描述。
     */
    private static final Map<Class<?>, LambdaBeanDesc> BEAN_DESC_CACHE = new WeakConcurrentMap<>();

    /**
     * deepCopy
     *
     * @param source 源数据
     * @return T
     */
    public static <T extends Serializable> T deepCopy(T source) {
        return deepCopy(source, null);
    }

    /**
     * deepCopy
     *
     * @param source 源数据
     * @return T
     */
    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static <T extends Serializable> T deepCopy(T source, Consumer<? super T> afterCopied) {
        if (Objects.isNull(source)) {
            return null;
        }

        T copied = (T) COPY_MAPPER.readValue(COPY_MAPPER.writeValueAsBytes(source), source.getClass());
        if (Objects.nonNull(copied) && Objects.nonNull(afterCopied)) {
            afterCopied.accept(copied);
        }

        return copied;
    }


    /**
     * 获取字段的getter方法（function）
     *
     * @param field 字段
     * @param <T>   实体类型
     * @param <U>   字段类型
     * @return Function<T, U>
     */
    public static <T, U> Function<T, U> getGetter(@NonNull Field field) {
        return (Function<T, U>) getGetter(field.getDeclaringClass(), field.getName());
    }

    /**
     * 获取字段的getter方法（function）
     *
     * @param clazz     类
     * @param fieldName 字段名称
     * @param <T>       实体类型
     * @param <U>       字段类型
     * @return Function<T, U>
     */
    @SneakyThrows
    public static <T, U> Function<T, U> getGetter(@NonNull final Class<T> clazz, @NonNull final String fieldName) {
        Function<T, U> getter = getLambdaBeanDesc(clazz).getGetter(fieldName);
        if (Objects.isNull(getter)) {
            throw new NoSuchMethodException("no getter method for field：" + fieldName);
        }

        return getter;
    }

    /**
     * 获取字段的setter方法（BiConsumer）
     *
     * @param field 字段
     * @param <T>   实体类型
     * @param <U>   字段类型
     * @return BiConsumer<T, U>
     */
    @SneakyThrows
    public static <T, U> BiConsumer<T, U> getSetter(@NonNull Field field) {
        return (BiConsumer<T, U>) getSetter(field.getDeclaringClass(), field.getName());
    }

    /**
     * 获取字段的setter方法（BiConsumer）
     *
     * @param clazz     类
     * @param fieldName 字段名称
     * @param <T>       实体类型
     * @param <U>       字段类型
     * @return BiConsumer<T, U>
     */
    @SneakyThrows
    public static <T, U> BiConsumer<T, U> getSetter(@NonNull final Class<T> clazz, @NonNull final String fieldName) {
        BiConsumer<T, U> setter = getLambdaBeanDesc(clazz).getSetter(fieldName);
        if (Objects.isNull(setter)) {
            throw new NoSuchMethodException("no setter method for field：" + fieldName);
        }

        return setter;
    }

    /**
     * 获取{@link LambdaBeanDesc} Bean描述信息
     *
     * @param clazz 类
     * @return LambdaBeanDesc
     */
    public static LambdaBeanDesc getLambdaBeanDesc(@NonNull final Class<?> clazz) {
        return MapUtils.computeIfAbsent(BEAN_DESC_CACHE, clazz, k -> {
            LambdaBeanDesc meta = new LambdaBeanDesc();
            Collection<PropDesc> props = BeanUtils.getBeanDesc(clazz).getProps();

            if (CollectionUtils.isNotEmpty(props)) {
                MethodHandles.Lookup lookup = MethodHandleUtil.lookup(clazz);
                for (PropDesc prop : props) {
                    try {
                        Method getterMethod = prop.getGetter();
                        Method setterMethod = prop.getSetter();
                        Function<Object, Object> getter = ObjectUtils.applyIfNotEmpty(getterMethod, x -> getGetter(lookup, getterMethod));
                        BiConsumer<Object, Object> setter = ObjectUtils.applyIfNotEmpty(setterMethod, x -> getSetter(lookup, setterMethod));
                        meta.addPropDesc(prop.getFieldName(), LambdaPropDesc.with(prop.getFieldName(), getter, setter));
                    } catch (Exception ignored) {

                    }
                }
            }

            return meta;
        }, true);
    }

    /**
     * 获取字段的getter方法（function）
     *
     * @param lookup 查找器
     * @param method 方法
     * @param <T>    实体类型
     * @param <U>    字段类型
     * @return BiConsumer<T, U>
     */
    @SneakyThrows
    public static <T, U> Function<T, U> getGetter(@NonNull final MethodHandles.Lookup lookup, @NonNull final Method method) {
        MethodHandle mt = lookup.unreflect(method);
        MethodType type = mt.type();
        if (type.hasPrimitives()) {
            type = type.wrap();
        }

        return (Function<T, U>) LambdaMetafactory.metafactory(lookup, "apply", methodType(Function.class), type.erase(),
                mt, type).getTarget().invokeExact();
    }

    /**
     * 获取字段的setter方法（BiConsumer）
     *
     * @param lookup 查找器
     * @param method 方法
     * @param <T>    实体类型
     * @param <U>    字段类型
     * @return BiConsumer<T, U>
     */
    @SneakyThrows
    public static <T, U> BiConsumer<T, U> getSetter(@NonNull final MethodHandles.Lookup lookup, @NonNull final Method method) {
        MethodHandle handle = lookup.unreflect(method);
        MethodType type = handle.type();

        if (type.hasPrimitives()) {
            type = type.wrap().changeReturnType(void.class);
        }

        return (BiConsumer<T, U>) LambdaMetafactory.metafactory(lookup, "accept", methodType(BiConsumer.class),
                type.erase(), handle, type).getTarget().invokeExact();
    }

    /**
     * 判断source与target的所有指定的公共字段的值是否相同
     *
     * @param source      源实体
     * @param target      目标实体
     * @param includeFieldList 指定的公共字段
     *
     * @return boolean
     */
    public static boolean isCommonFieldsEqual(Object source, Object target, List<String> includeFieldList) {
        if (Objects.isNull(source) && Objects.isNull(target)) {
            return true;
        }

        if (Objects.isNull(source) || Objects.isNull(target)) {
            return false;
        }

        Map<String, Object> sourceFieldsMap = beanToMap(source);
        Map<String, Object> targetFieldsMap = beanToMap(target);

        for (String field : includeFieldList) {
            if (ObjectUtil.notEqual(sourceFieldsMap.get(field), targetFieldsMap.get(field))) {
                return false;
            }
        }

        return true;
    }

}
