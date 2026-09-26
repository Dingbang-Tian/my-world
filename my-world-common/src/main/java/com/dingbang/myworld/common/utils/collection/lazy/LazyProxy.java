package com.dingbang.myworld.common.utils.collection.lazy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.LazyLoader;

import org.springframework.lang.NonNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 延迟加载代理工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class LazyProxy {

    /**
     * 判断是否是懒加载对象
     *
     * @param object
     * @return
     */
    public static boolean isLazy(@NonNull Object object) {
        return object instanceof LazyLoadable;
    }

    /**
     * 创建懒加载集合List
     *
     * @param listSupplier
     * @param <T>
     * @return
     */
    public static <T> List<T> listOf(Supplier<List<T>> listSupplier) {
        return LazyList.of(listSupplier);
    }

    /**
     * 创建懒加载集合Set
     *
     * @param setSupplier
     * @param <T>
     * @return
     */
    public static <T> Set<T> setOf(Supplier<Set<T>> setSupplier) {
        return LazySet.of(setSupplier);
    }

    /**
     * 创建懒加载集合Map
     *
     * @param setSupplier
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> Map<K, V> mapOf(Supplier<Map<K, V>> setSupplier) {
        return LazyMap.of(setSupplier);
    }

    /**
     * 创建懒加载代理对象
     *
     * @param clazz
     * @param lazyLoader
     * @param <T>
     * @return
     */
    public static <T> T of(Class<T> clazz, LazyLoader lazyLoader) {
        Enhancer enhancer = new Enhancer();
        if (clazz.isInterface()) {
            enhancer.setInterfaces(new Class[]{LazyLoadable.class, clazz});
        } else {
            enhancer.setSuperclass(clazz);
            enhancer.setInterfaces(new Class[]{LazyLoadable.class});
        }

        enhancer.setCallback(new ILazyLoader(lazyLoader));
        return (T) enhancer.create();
    }

    /**
     * 判断是否完成懒加载，如果不是懒加载代理对象，会返回true
     *
     * @param proxy
     * @return
     */
    public static boolean isLoaded(Object proxy) {
        if (proxy instanceof LazyContainer) {
            return ((LazyContainer) proxy).getLazy().isEvaluated();
        }
        if (proxy instanceof LazyLoadable) {
            Callback callback = ((Factory) proxy).getCallback(0);
            return ((ILazyLoader) callback).isLoaded();
        }
        return true;
    }

    /**
     * CGLIB 延迟加载回调实现。
     *
     * @author Sebastian
     * @since 2026/09/25
     */
    @Getter
    @RequiredArgsConstructor
    public static class ILazyLoader implements LazyLoader {

        private final LazyLoader lazyLoader;
        private boolean loaded = false;

        @Override
        public Object loadObject() throws Exception {
            Object result = lazyLoader.loadObject();
            loaded = true;
            return result;
        }
    }

}
