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

package org.jboss.remoting3;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A set of attachments for an entity.
 */
public final class Attachments {
    private final ConcurrentMap<Key<?>, Object> map = new ConcurrentHashMap<Key<?>, Object>();

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
