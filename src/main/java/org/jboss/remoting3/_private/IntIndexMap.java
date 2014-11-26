/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3._private;

import java.util.Collection;

/**
 * A map which is indexed by integer value and thus acts similarly to a set.
 *
 * @param <E> the element type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface IntIndexMap<E> extends Iterable<E>, IntIndexer<E>, Collection<E> {

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
