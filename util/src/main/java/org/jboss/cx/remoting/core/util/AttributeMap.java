package org.jboss.cx.remoting.core.util;

import java.util.Set;
import java.util.Collection;

/**
 *
 */
public interface AttributeMap {

    <T> T get(AttributeKey<T> key);

    <T> T put(AttributeKey<T> key, T value);

    <T> T remove(AttributeKey<T> key);

    <T> boolean remove(AttributeKey<T> key, T value);

    <T> T putIfAbsent(AttributeKey<T> key, T value);

    <T> boolean replace(AttributeKey<T> key, T oldValue, T newValue);

    <T> boolean containsKey(AttributeKey<T> key);

    <T> boolean containsValue(T value);

    Iterable<Entry<?>> entries();

    Set<AttributeKey<?>> keySet();

    Collection<?> values();

    boolean isEmpty();

    int size();

    void clear();

    interface Entry<T> {
        AttributeKey<T> getKey();

        T getValue();

        void setValue(T newValue);
    }
}
