package com.dingbang.myworld.common.utils.bean;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 支持 Lambda 属性解析的属性描述类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Data
@RequiredArgsConstructor(staticName = "with")
public class LambdaPropDesc {
    /**
     * 字段
     */
    private final String fieldName;
    /**
     * Getter方法
     */
    private final Function<?,?> getter;
    /**
     * Setter方法
     */
    private final BiConsumer<?,?> setter;
}
