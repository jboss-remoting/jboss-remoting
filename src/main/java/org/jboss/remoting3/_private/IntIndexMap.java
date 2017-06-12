/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3._private;

import java.util.Collection;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * A map which is indexed by integer value and thus acts similarly to a set.
 *
 * @param <E> the element type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface IntIndexMap<E> extends Iterable<E>, ToIntFunction<E>, Collection<E> {

    /**
     * Determine whether the given index is contained in the map.
     *
     * @param index the index
     * @return {@code true} if it is contained in the map
     */
    boolean containsKey(int index);

    /**
     * Get the value for the given index.
     *
     * @param index the index
     * @return the corresponding value
     */
    E get(int index);

    /**
     * Remove and return a value for the given index.
     *
     * @param index the index
     * @return the value
     */
    E removeKey(int index);

    /**
     * Put a value into the map, replacing and returning any existing mapping.
     *
     * @param value the value to add
     * @return the old value, or {@code null} if the old value was {@code null} or was not present
     */
    E put(E value);

    boolean remove(Object o);

    /**
     * Put a value into the map if there is not already an existing mapping for it.
     *
     * @param value the value to add
     * @return the existing value, if any, or {@code null} if the existing value was {@code null} or the value was added successfully
     */
    E putIfAbsent(E value);

    /**
     * Compute a map value if no mapping exists.
     *
     * @param key the key
     * @param producer the producer which creates a new value
     * @return the existing or new value
     */
    E computeIfAbsent(int key, IntFunction<E> producer);

    /**
     * Put a value into the map only if there is an existing mapping for it.
     *
     * @param value the value to store
     * @return the previous value (may be {@code null}) or {@code null} if there was no mapping to replace
     */
    E replace(E value);

    /**
     * Replace an old value with a new value.
     *
     * @param oldValue the value to replace
     * @param newValue the value to replace with
     * @return {@code true} if the replacement succeeded, or {@code false} if the old value was not present in the map
     */
    boolean replace(E oldValue, E newValue);
}
