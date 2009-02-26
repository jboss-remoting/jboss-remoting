/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.spi;

import org.jboss.remoting3.HandleableCloseable;
import java.io.IOException;

/**
 * A closeable resource which closes automatically when the number of active handles reaches zero.  Handles are considered
 * active until they are closed.
 */
public interface AutoCloseable<T> extends HandleableCloseable<T> {

    /**
     * Get a handle to this resource.  When the number of open handles reaches zero, the resource will be closed.
     *
     * @return a handle
     * @throws IOException if an error occurs, particularly if this resource is already closed
     */
    Handle<T> getHandle() throws IOException;
}
