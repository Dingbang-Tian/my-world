package com.dingbang.myworld.common.utils.lang;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.hutool.core.lang.func.Func1;
import com.dingbang.myworld.common.utils.collection.ListUtils;
import com.dingbang.myworld.common.utils.collection.MapUtils;
import lombok.NonNull;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 注解操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class AnnotationUtils extends AnnotationUtil {

    /**
     * 根据注解对指定类的字段进行处理
     *
     * @param targetClass     目标类
     * @param annotationClass 注解类
     * @param fieldCallback   字段回调
     */
    public static <A extends Annotation> void doWithAnnotation(Class<?> targetClass, Class<A> annotationClass, BiConsumer<Field, A> fieldCallback) {
        ReflectionUtils.doWithFields(targetClass, field -> {
            A annotation = field.getAnnotation(annotationClass);
            if (Objects.isNull(annotation)) {
                return;
            }

            fieldCallback.accept(field, annotation);
        });
    }

    /**
     * 根据注解获取指定类的字段列表
     *
     * @param targetClass     目标类
     * @param annotationClass 注解类
     * @return List<String>
     */
    public static <A extends Annotation> List<String> getFieldsByAnnotation(Class<?> targetClass, Class<A> annotationClass) {
        return getFieldsByAnnotation(targetClass, annotationClass, null);
    }

    /**
     * 根据注解获取指定类的字段列表
     *
     * @param targetClass     目标类
     * @param annotationClass 注解类
     * @param fieldFilter     字段过滤
     * @return List<String>
     */
    public static <A extends Annotation> List<String> getFieldsByAnnotation(Class<?> targetClass, Class<A> annotationClass, Predicate<A> fieldFilter) {
        List<String> fieldList = ListUtils.toList();
        ReflectionUtils.doWithFields(targetClass, field -> {
            A annotation = field.getAnnotation(annotationClass);

            if (Objects.nonNull(annotation)) {
                if (Objects.nonNull(fieldFilter) && !fieldFilter.test(annotation)) {
                    return;
                }

                fieldList.add(field.getName());
            }
        });

        return fieldList;
    }

    public static Set<AnnotationAttributes> attributesForRepeatable(@NonNull Class<?> clazz,
        @NonNull Class<? extends Annotation> annotationClass, @NonNull Class<? extends Annotation> containerClass) {

        Set<AnnotationAttributes> result = findAnnotations(clazz, annotationClass, containerClass).stream()
                .map(MergedAnnotation::asAnnotationAttributes)
                .filter(MapUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return Collections.unmodifiableSet(result);
    }

    /**
     * 获取注解信息
     *
     * @param annotatedElement class、method、field、constructor
     * @param annotationClass  注解类型
     * @param <A>
     * @return
     */
    public static <A extends Annotation> MergedAnnotation<A> findAnnotation(AnnotatedElement annotatedElement, @NonNull Class<A> annotationClass) {
        return MergedAnnotations.from(annotatedElement, MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS).get(annotationClass);
    }

    /**
     * 获取重复注解信息
     *
     * @param annotatedElement class、method、field、constructor
     * @param annotationClass  注解类型
     * @param containerClass   容器类型 {@link Repeatable#value()}
     * @param <A>
     * @param <CONTAINER>
     * @return
     */
    public static <A extends Annotation, CONTAINER extends Annotation> List<MergedAnnotation<A>> findAnnotations(
            AnnotatedElement annotatedElement, @NonNull Class<A> annotationClass, @NonNull Class<CONTAINER> containerClass) {

        return MergedAnnotations.from(annotatedElement, MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS,
                        RepeatableContainers.of(annotationClass, containerClass))
                .stream(annotationClass)
                .collect(Collectors.toList());
    }

    /**
     * 获取注解方法名称
     *
     * @param getter
     * @return String
     */
    public static <A extends Annotation, T> String getMethodName(@NonNull Func1<A, T> getter) {
        return LambdaUtils.getMethodName(getter);
    }

}
