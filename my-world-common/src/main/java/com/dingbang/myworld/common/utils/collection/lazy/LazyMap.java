package com.dingbang.myworld.common.utils.collection.lazy;

import com.google.common.collect.Maps;
import io.vavr.Lazy;
import lombok.Getter;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 延迟加载映射实现。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Getter
@SuppressWarnings("NullableProblems")
public class LazyMap<K, V> implements Map<K, V> ,LazyContainer<Map<K, V>>, Serializable {
    protected final Lazy<Map<K, V>> lazy;

    public static <K, V> LazyMap<K, V> of(Supplier<Map<K, V>> lazy) {
        return new LazyMap<>(lazy);
    }

    public LazyMap() {
        this.lazy = Lazy.of(Maps::newHashMap);
    }

    public LazyMap(Supplier<Map<K, V>> lazy) {
        this.lazy = Lazy.of(lazy);
    }

    @Override
    public int size() {
        return container().size();
    }

    @Override
    public boolean isEmpty() {
        return container().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return container().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return container().containsValue(value);
    }

    @Override
    public V get(Object key) {
        return container().get(key);
    }

    @Override
    public V put(K key, V value) {
        return container().put(key, value);
    }

    @Override
    public V remove(Object key) {
        return container().remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        container().putAll(m);
    }

    @Override
    public void clear() {
        container().clear();
    }

    @Override
    public Set<K> keySet() {
        return container().keySet();
    }

    @Override
    public Collection<V> values() {
        return container().values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return container().entrySet();
    }

    @Override
    public Map<K, V> container() {
        return Objects.requireNonNull(this.lazy.get());
    }
}
