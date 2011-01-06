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

package org.jboss.remoting3.stream;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * A streaming sink for objects.
 *
 * @param <T> the object type
 */
public interface ObjectSink<T> extends Flushable, Closeable {

    /**
     * Accept an object.
     *
     * @param instance the object to accept
     * @throws IOException if an error occurs
     */
    void accept(T instance) throws IOException;

    /**
     * Push out any temporary state.  May be a no-op on some implementations.
     *
     * @throws IOException if an error occurs
     */
    void flush() throws IOException;

    /**
     * Close the sink.
     *
     * @throws IOException if an error occurs
     */
    void close() throws IOException;
}
