package com.dingbang.myworld.common.utils.bean;

import lombok.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 支持 Lambda 属性解析的 Bean 描述类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@SuppressWarnings("unchecked")
public class LambdaBeanDesc {

    /**
     * 属性Map
     */
    private final Map<String, LambdaPropDesc> propMap = new LinkedHashMap<>();

    /**
     * 获取Getter方法，如果不存在返回null
     *
     * @param fieldName 字段名
     * @return Getter方法
     */
    public <T, U> Function<T, U> getGetter(String fieldName) {
        final LambdaPropDesc desc = this.propMap.get(fieldName);
        return null == desc ? null : (Function<T, U>) desc.getGetter();
    }

    /**
     * 获取Setter方法，如果不存在返回null
     *
     * @param fieldName 字段名
     * @return Setter方法
     */
    public <T, U> BiConsumer<T, U> getSetter(String fieldName) {
        final LambdaPropDesc desc = this.propMap.get(fieldName);
        return null == desc ? null : (BiConsumer<T, U>) desc.getSetter();
    }

    /**
     * 新增一个属性
     * @param fieldName
     * @param propDesc
     */
    public void addPropDesc(String fieldName, @NonNull LambdaPropDesc propDesc){
        propMap.put(fieldName,propDesc);
    }
}
