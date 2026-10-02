package com.dingbang.myworld.agent.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明模型可见的工具名称与用途。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ToolInfo {
    /**
     * 返回稳定的工具名称。
     *
     * @return 工具名称
     */
    String name();

    /**
     * 返回模型可见的用途说明。
     *
     * @return 用途说明
     */
    String description();
}
