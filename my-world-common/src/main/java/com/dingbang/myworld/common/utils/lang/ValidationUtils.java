package com.dingbang.myworld.common.utils.lang;

import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.util.ReflectUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.dingbang.myworld.common.utils.collection.ListUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.hibernate.validator.HibernateValidatorFactory;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 数据校验工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class ValidationUtils {

    /**
     * 默认校验器。不再从瑞幸 Spring 容器取名为 i18nValidatorFactory 的 Bean。
     */
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    public static <T> LinkedHashMap<String, List<String>> validate(T object) {
        return validate(object, Default.class);
    }

    /**
     * 基于 jakarta.validation.Validator 校验对象字段
     *
     * @param object 待验证的对象
     * @param groups 验证分组
     * @return Map<String, List<String>> {字段名：错误列表}
     */
    public static <T> LinkedHashMap<String, List<String>> validate(T object, Class<?>... groups) {
        LinkedHashMap<String, List<String>> errorMap = Maps.newLinkedHashMap();
        if (Objects.isNull(object)) {
            return errorMap;
        }

        HibernateValidatorFactory validatorFactory = VALIDATOR_FACTORY.unwrap(HibernateValidatorFactory.class);
        Validator validator = validatorFactory.usingContext().constraintValidatorPayload(object).getValidator();

        Set<ConstraintViolation<T>> violationSet = validator.validate(object, groups);
        if (CollectionUtils.isEmpty(violationSet)) {
            return errorMap;
        }

        // 按字段名顺序排序
        List<String> fieldNameList = ListUtils
            .distinctMapBy(ListUtils.toList(ReflectUtil.getFields(object.getClass())), Field::getName);

        return violationSet.stream()
            .sorted(Comparator.comparingInt(x -> fieldNameList.indexOf(x.getPropertyPath().toString())))
            .collect(Collectors.groupingBy(x -> x.getPropertyPath().toString(), LinkedHashMap::new,
                Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())));
    }

    /**
     * 获取字段名
     *
     * @param fieldFunctionMap 字段函数map
     * @param fieldFunction 字段函数
     * @return String
     */
    public static <T> String getFieldName(ConcurrentMap<Func1<T, ?>, String> fieldFunctionMap, Func1<T, ?> fieldFunction) {
        return fieldFunctionMap.computeIfAbsent(fieldFunction, LambdaUtils::getFieldName);
    }

    /**
     * 添加到校验错误结果map
     *
     * @param violationMap 校验错误结果map
     * @param field 字段
     * @param errorMsg 错误信息
     */
    public static void addToViolationMap(Map<String, List<String>> violationMap, String field, String errorMsg) {
        violationMap.computeIfAbsent(field, key -> Lists.newArrayList()).add(errorMsg);
    }

}
