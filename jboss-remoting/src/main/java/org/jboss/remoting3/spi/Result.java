/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

import java.io.IOException;

/**
 * A handler for accepting the result of an operation.  Used by protocol implementations to tell Remoting
 * the result of an operation.
 *
 * @param <T> the type of the result
 */
public interface Result<T> {

    /**
     * Indicate a successful result, and hand in the result value.
     *
     * @param result the result value
     */
    void setResult(T result);

    /**
     * Indicate a failure, and hand in the exception.
     *
     * @param exception the exception
     */
    void setException(IOException exception);

    /**
     * Indicate a cancellation of the operation.
     */
    void setCancelled();
}
