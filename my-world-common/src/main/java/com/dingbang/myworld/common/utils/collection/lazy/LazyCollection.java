package com.dingbang.myworld.common.utils.collection.lazy;

import com.google.common.collect.Sets;
import io.vavr.Lazy;
import lombok.Getter;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 延迟加载集合基础实现。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Getter
@SuppressWarnings("NullableProblems")
public class LazyCollection<E, C extends Collection<E>> implements Collection<E>,LazyContainer<C>, Serializable {

    protected final Lazy<C> lazy;

    protected LazyCollection(Supplier<C> lazy) {
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
    public boolean contains(Object o) {
        return container().contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return container().iterator();
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        container().forEach(action);
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
    public boolean containsAll(Collection<?> c) {
        return Sets.newHashSet(container()).containsAll(c);
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
    public boolean removeIf(Predicate<? super E> filter) {
        return container().removeIf(filter);
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

    @Override
    public Stream<E> stream() {
        return container().stream();
    }

    @Override
    public Stream<E> parallelStream() {
        return container().parallelStream();
    }

    @Override
    public C container() {
        return Objects.requireNonNull(this.lazy.get());
    }
}
