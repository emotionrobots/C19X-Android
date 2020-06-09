package org.c19x.data.primitive;

public interface TriConsumer<T, U, V> {
    void accept(T t, U u, V v);
}
