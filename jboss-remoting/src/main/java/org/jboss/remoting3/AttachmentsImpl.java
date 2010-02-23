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

import java.util.IdentityHashMap;
import java.util.Map;

final class AttachmentsImpl implements Attachments {
    private final Map<Key<?>, Object> map = new IdentityHashMap<Key<?>, Object>();

    public <T> T attach(final Key<T> key, final T value) {
        final Map<Key<?>, Object> map = this.map;
        synchronized (map) {
            final Class<T> type = key.getType();
            return type.cast(map.put(key, type.cast(value)));
        }
    }

    public <T> T attachIfAbsent(final Key<T> key, final T value) {
        final Map<Key<?>, Object> map = this.map;
        synchronized (map) {
            final Object old = map.get(key);
            if (old != null) {
                return key.getType().cast(old);
            } else {
                map.put(key, key.getType().cast(value));
                return null;
            }
        }
    }

    public <T> boolean replaceAttachment(final Key<T> key, final T expect, final T replacement) {
        final Map<Key<?>, Object> map = this.map;
        synchronized (map) {
            final Object old = map.get(key);
            if (expect == old || expect != null && expect.equals(old)) {
                map.put(key, key.getType().cast(replacement));
                return true;
            } else {
                return false;
            }
        }
    }

    public <T> T removeAttachment(final Key<T> key) {
        final Map<Key<?>, Object> map = this.map;
        synchronized (map) {
            return key.getType().cast(map.remove(key));
        }
    }

    public <T> boolean removeAttachment(final Key<T> key, final T value) {
        final Map<Key<?>, Object> map = this.map;
        synchronized (map) {
            final Object old = map.get(key);
            if (value == old || value != null && value.equals(old)) {
                map.remove(key);
                return false;
            }
            return true;
        }
    }

    public <T> T getAttachment(final Key<T> key) {
        final Map<Key<?>, Object> map = this.map;
        synchronized (map) {
            return key.getType().cast(map.get(key));
        }
    }
}
