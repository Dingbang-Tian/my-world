package com.dingbang.myworld.common.utils;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.beans.BeanCopier;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 对象属性复制工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Slf4j
public class CopierUtils {
    /**
     * 单个对象属性拷贝
     * @param source 源对象
     * @param clazz 目标对象Class
     * @param <T> 目标对象类型
     * @param <M> 源对象类型
     * @return 目标对象
     */
    public static <T, M> T copyProperties(M source, Class<T> clazz){
        if (Objects.isNull(source) || Objects.isNull(clazz)) {
            return null;
        }
        return copyProperties(source, clazz, null);
    }

    /**
     * 列表对象拷贝
     * @param sources 源列表
     * @param clazz 源列表对象Class
     * @param <T> 目标列表对象类型
     * @param <M> 源列表对象类型
     * @return 目标列表
     */
    public static <T, M> List<T> copyObjects(List<M> sources, Class<T> clazz) {
        if (CollectionUtils.isEmpty(sources)) {
            return Lists.newArrayList();
        }
        BeanCopier copier = BeanCopier.create(sources.get(0).getClass(), clazz, false);
        return Optional.of(sources)
                .orElse(new ArrayList<>())
                .stream().map(m -> copyProperties(m, clazz, copier))
                .collect(Collectors.toList());
    }

    /**
     * 单个对象属性拷贝
     * @param source 源对象
     * @param clazz 目标对象Class
     * @param copier copier
     * @param <T> 目标对象类型
     * @param <M> 源对象类型
     * @return 目标对象
     */
    private static <T, M> T copyProperties(M source, Class<T> clazz, BeanCopier copier){
        if (null == copier){
            copier = BeanCopier.create(source.getClass(), clazz, false);
        }
        T t = null;
        try {
            t = clazz.newInstance();
            copier.copy(source, t, null);
        } catch (InstantiationException | IllegalAccessException e) {
            log.error("copy properties error", e);
        }
        return t;
    }
}
