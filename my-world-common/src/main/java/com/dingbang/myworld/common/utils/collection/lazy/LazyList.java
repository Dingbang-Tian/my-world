package com.dingbang.myworld.common.utils.collection.lazy;

import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

/**
 * 延迟加载列表实现。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@SuppressWarnings("NullableProblems")
public class LazyList<E> extends LazyCollection<E, List<E>> implements List<E> {


    public static <E> LazyList<E> of(Supplier<List<E>> lazy) {
        return new LazyList<>(lazy);
    }

    public LazyList() {
        this(Lists::newArrayList);
    }
    public LazyList(Supplier<List<E>> lazy) {
        super(lazy);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        return container().addAll(index, c);
    }

    @Override
    public E get(int index) {
        return container().get(index);
    }

    @Override
    public E set(int index, E element) {
        return container().set(index, element);
    }

    @Override
    public void add(int index, E element) {
        container().add(index, element);
    }

    @Override
    public E remove(int index) {
        return container().remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return container().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return container().lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return container().listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return container().listIterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return container().subList(fromIndex, toIndex);
    }
}
