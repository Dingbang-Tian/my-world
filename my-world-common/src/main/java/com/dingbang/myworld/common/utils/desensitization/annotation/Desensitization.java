package com.dingbang.myworld.common.utils.desensitization.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.dingbang.myworld.common.utils.desensitization.enums.DesensitizationType;
import com.dingbang.myworld.common.utils.desensitization.json.DesensitizationSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段数据脱敏注解。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = DesensitizationSerializer.class)
public @interface Desensitization {

    /**
     * 脱敏数据类型(没给默认值，所以使用时必须指定type)
     *
     * @return
     */
    DesensitizationType type();

    /**
     * 前置不需要打码的长度
     *
     * @return
     */
    int prefixNoMaskLen() default 1;

    /**
     * 后置不需要打码的长度
     *
     * @return
     */
    int suffixNoMaskLen() default 1;

    /**
     * 用什么打码
     *
     * @return
     */
    String symbol() default "*";
}
