package org.jboss.cx.remoting.core.util;

import java.util.Set;
import java.util.Map;
import java.util.Collection;

/**
 *
 */
public interface TypeMap<B> {
    void clear();

    boolean containsKey(Class<?> key);

    boolean containsValue(Object value);

    Set<Entry<? extends B>> entrySet();

    <T extends B> T get(Class<T> key);

    boolean isEmpty();

    Set<Class<? extends B>> keySet();

    <T extends B> T put(Class<T> key, T value);

    <T extends B> void putAll(TypeMap<T> m);

    <T extends B> T remove(Class<T> key);

    int size();

    Collection<? extends B> values();

    <T extends B> T putIfAbsent(Class<T> key, T value);

    <T extends B> boolean remove(Class<T> key, Object value);

    <T extends B> T replace(Class<T> key, T value);

    <T extends B> boolean replace(Class<T> key, T oldValue, T newValue);

    interface Entry<T> extends Map.Entry<Class<T>, T> {
    }
}
