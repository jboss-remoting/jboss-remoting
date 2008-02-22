package org.jboss.cx.remoting.util;

import java.util.Collection;
import java.util.Set;

/**
 * A map whose value types are determined by the key.
 */
public interface AttributeMap {

    /**
     * Get a value from the map.
     *
     * @param key the key
     * @return the value
     */
    <T> T get(AttributeKey<T> key);

    /**
     * Store a value into the map.  Any previous mapping for this value is overwritten.
     *
     * @param key the key
     * @param value the new value
     * @return the old value (may be {@code null}), or {@code null} if there was no mapping for this key
     */
    <T> T put(AttributeKey<T> key, T value);

    /**
     * Remove a mapping from the map.
     *
     * @param key the key
     * @return the old value (may be {@code null}), or {@code null} if there was no mapping for this key
     */
    <T> T remove(AttributeKey<T> key);

    /**
     * Remove a mapping from the map.  Both the key and value must match the values given.
     *
     * @param key the key
     * @param value the value
     * @return {@code true} if a matching mapping was located and removed
     */
    <T> boolean remove(AttributeKey<T> key, T value);

    /**
     * Store a value into the map if there is no value currently stored.
     *
     * @param key the key
     * @param value the value
     * @return the old value if there was a previous mapping
     */
    <T> T putIfAbsent(AttributeKey<T> key, T value);

    /**
     * Replace a mapping in the map.
     *
     * @param key the key
     * @param oldValue the old value
     * @param newValue the replacement value
     * @return {@code true} if a matching mapping was located and replaced
     */
    <T> boolean replace(AttributeKey<T> key, T oldValue, T newValue);

    /**
     * Test the map for the presence of a key.
     *
     * @param key the key
     * @return {@code true} if the key is present in the map
     */
    <T> boolean containsKey(AttributeKey<T> key);

    /**
     * Test the map for the presence of a value.
     *
     * @param value the value
     * @return {@code true} if the value is present in the map
     */
    <T> boolean containsValue(T value);

    /**
     * Get all the map entries.
     *
     * @return the entries
     */
    Iterable<Entry<?>> entries();

    /**
     * Get the key set for this map.  The returned set supports all set operations except for {@code add} and {@code addAll}.
     *
     * @return the key set
     */
    Set<AttributeKey<?>> keySet();

    /**
     * Get the collection of values for this map.
     *
     * @return the values
     */
    Collection<?> values();

    /**
     * Determine whether this map is empty.
     *
     * @return {@code true} if the map has no mappings
     */
    boolean isEmpty();

    /**
     * Determine the number of entries in the map.
     *
     * @return the number of entries
     */
    int size();

    /**
     * Clear the map of all mappings.
     */
    void clear();

    /**
     * An entry in the {@code AttributeMap}.
     */
    interface Entry<T> {
        /**
         * Get the entry key.
         *
         * @return the key
         */
        AttributeKey<T> getKey();

        /**
         * Get the entry value.
         *
         * @return the value
         */
        T getValue();

        /**
         * Change the entry value.
         *
         * @param newValue the new value
         */
        void setValue(T newValue);
    }
}
