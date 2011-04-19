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
 * Attachments on an entity.
 */
public interface Attachments {

    /**
     * Attach a value to this object.
     *
     * @param key the attachment key
     * @param value the attachment value
     * @param <T> the attachment value type
     * @return the old value, if any
     */
    <T> T attach(Key<T> key, T value);

    /**
     * Attach a value to this object if one was not previously attached.
     *
     * @param key the attachment key
     * @param value the attachment value
     * @param <T> the attachment value type
     * @return the existing value, or {@code null} if the attachment succeeded
     */
    <T> T attachIfAbsent(Key<T> key, T value);

    /**
     * Replace an old attachment with a new one.
     *
     * @param key the attachment key
     * @param expect the expected attachment
     * @param replacement the replacement attachment
     * @param <T> the attachment value type
     * @return {@code true} if the attachment was replaced, {@code false} if the expected value was not found
     */
    <T> boolean replaceAttachment(Key<T> key, T expect, T replacement);

    /**
     * Remove an attachment.
     *
     * @param key the attachment key
     * @param <T> the attachment value type
     * @return the old value or {@code null} if there was none
     */
    <T> T removeAttachment(Key<T> key);

    /**
     * Remove an attachment with an expected value.
     *
     * @param key the attachment key
     * @param value the expected attachment value
     * @param <T> the attachment value type
     * @return {@code true} if the attachment was removed, {@code false} if the expected value was not found
     */
    <T> boolean removeAttachment(Key<T> key, T value);

    /**
     * Get an attachment value.
     *
     * @param key the attachment key
     * @param <T> the attachment value type
     * @return the attachment value
     */
    <T> T getAttachment(Key<T> key);

    /**
     * An attachment key.
     *
     * @param <T> the attachment value type
     */
    final class Key<T> {
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
