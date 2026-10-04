package com.dingbang.myworld.common.utils.collection.lazy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.sf.cglib.proxy.LazyLoader;

/**
 * CGLIB 延迟加载回调实现。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Getter
@RequiredArgsConstructor
public class LazyLoaderAdapter implements LazyLoader {

    /**
     * 实际加载目标值的回调。
     */
    private final LazyLoader lazyLoader;
    /**
     * 目标值是否已完成加载。
     */
    private boolean loaded = false;

    @Override
    public Object loadObject() throws Exception {
        Object result = lazyLoader.loadObject();
        loaded = true;
        return result;
    }
}
