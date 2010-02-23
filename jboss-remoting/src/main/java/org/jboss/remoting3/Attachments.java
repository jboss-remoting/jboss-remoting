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

/**
 *
 */
public interface Attachments {

    /**
     * Attach a value to this object.
     *
     * @param key the attachment key
     * @param value the attachment value
     * @param <T> the type of the attachment
     * @returns the old value, if any
     */
    <T> T attach(Key<T> key, T value);

    <T> T attachIfAbsent(Key<T> key, T value);

    <T> boolean replaceAttachment(Key<T> key, T expect, T replacement);

    <T> T removeAttachment(Key<T> key);

    <T> boolean removeAttachment(Key<T> key, T value);

    <T> T getAttachment(Key<T> key);

    final class Key<T> {
        private final Class<T> type;

        public Key(final Class<T> type) {
            this.type = type;
        }

        public Class<T> getType() {
            return type;
        }
    }
}
