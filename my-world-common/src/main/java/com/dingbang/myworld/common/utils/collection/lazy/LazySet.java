package com.dingbang.myworld.common.utils.collection.lazy;


import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Supplier;

/**
 * 延迟加载集合去重实现。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@SuppressWarnings("NullableProblems")
public class LazySet<E> extends LazyCollection<E, Set<E>> implements Set<E> {
    public static <E> LazySet<E> of(Supplier<Set<E>> lazy) {
        return new LazySet<>(lazy);
    }

    public LazySet() {
        this(Sets::newHashSet);
    }

    public LazySet(Supplier<Set<E>> lazy) {
        super(lazy);
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
    public boolean contains(Object o) {
        return container().contains(o);
    }


    @Override
    public Iterator<E> iterator() {
        return container().iterator();
    }


    @Override
    public Object[] toArray() {
        return container().toArray();
    }


    @Override
    public <T1> T1[] toArray(T1[] a) {
        return container().toArray(a);
    }

    @Override
    public boolean add(E e) {
        return container().add(e);
    }

    @Override
    public boolean remove(Object o) {
        return container().remove(o);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return container().addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return container().removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return container().retainAll(c);
    }

    @Override
    public void clear() {
        container().clear();
    }

    @Override
    public Spliterator<E> spliterator() {
        return container().spliterator();
    }
}
