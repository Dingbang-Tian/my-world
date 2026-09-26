package com.dingbang.myworld.common.utils.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 日志序列化字段排除注解。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Inherited
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExclude {

    /**
     * 日志中的占位内容
     *
     * @return placeholder
     */
    String placeholder() default "[omitted]";
}
