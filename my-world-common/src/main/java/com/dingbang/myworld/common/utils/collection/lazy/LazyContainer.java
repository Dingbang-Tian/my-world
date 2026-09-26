package com.dingbang.myworld.common.utils.collection.lazy;

import io.vavr.Lazy;

/**
 * 延迟加载容器接口。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
interface LazyContainer<C> extends LazyLoadable {

    /**
     * container
     *
     * @return
     */
    C container();

    /**
     * 获取lazy类
     *
     * @return
     */
    Lazy<C> getLazy();

}
