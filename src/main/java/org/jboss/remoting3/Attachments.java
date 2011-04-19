/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.jboss.remoting3;

import java.util.concurrent.ConcurrentMap;

/**
 * A set of attachments for an entity.
 */
public final class Attachments {
    private final ConcurrentMap<Key<?>, Object> map = new UnlockedReadHashMap<Key<?>, Object>();

    /** {@inheritDoc} */
    public <T> T attach(final Key<T> key, final T value) {
        final Class<T> type = key.getType();
        return type.cast(map.put(key, type.cast(value)));
    }

    /** {@inheritDoc} */
    public <T> T attachIfAbsent(final Key<T> key, final T value) {
        final Class<T> type = key.getType();
        return type.cast(map.putIfAbsent(key, type.cast(value)));
    }

    /** {@inheritDoc} */
    public <T> boolean replaceAttachment(final Key<T> key, final T expect, final T replacement) {
        return map.replace(key, expect, key.getType().cast(replacement));
    }

    /** {@inheritDoc} */
    public <T> T removeAttachment(final Key<T> key) {
        return key.getType().cast(map.remove(key));
    }

    /** {@inheritDoc} */
    public <T> boolean removeAttachment(final Key<T> key, final T value) {
        return map.remove(key, value);
    }

    /** {@inheritDoc} */
    public <T> T getAttachment(final Key<T> key) {
        return key.getType().cast(map.get(key));
    }

    /**
     * An attachment key.
     *
     * @param <T> the attachment value type
     */
    public static final class Key<T> {
        private final Class<T> type;

        /**
         * Construct a new instance.
         *
         * @param type the key type class.
         */
        public Key(final Class<T> type) {
            this.type = type;
        }

        /**
         * Get the attachment type class.
         *
         * @return the attachment type class
         */
        public Class<T> getType() {
            return type;
        }
    }
}
