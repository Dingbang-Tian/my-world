package com.dingbang.myworld.agent.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个模型可填写的工具参数字段。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ToolParam {
    /**
     * 返回字段用途说明。
     *
     * @return 用途说明
     */
    String description();

    /**
     * 返回字段是否必须出现且不得为 null。
     *
     * @return 是否必填
     */
    boolean required() default true;
}
