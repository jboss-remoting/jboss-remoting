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

import java.net.URI;

/**
 * Service not found.  This exception is thrown when a service is looked up which is not registered anywhere.
 */
public class ServiceNotFoundException extends ServiceOpenException {

    private static final long serialVersionUID = -998858276817298658L;

    private final URI serviceUri;

    /**
     * Constructs a <tt>ServiceNotFoundException</tt> with no detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param uri the service URI that could not be found
     */
    public ServiceNotFoundException(final URI uri) {
        serviceUri = uri;
    }

    /**
     * Constructs a <tt>ServiceNotFoundException</tt> with the specified detail message. The cause is not initialized, and
     * may subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param uri the service URI that could not be found
     * @param msg the detail message
     */
    public ServiceNotFoundException(final URI uri, final String msg) {
        super(msg);
        serviceUri = uri;
    }

    /**
     * Constructs a <tt>ServiceNotFoundException</tt> with the specified cause. The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of <tt>cause</tt>).
     *
     * @param uri the service URI that could not be found
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public ServiceNotFoundException(final URI uri, final Throwable cause) {
        super(cause);
        serviceUri = uri;
    }

    /**
     * Constructs a <tt>ServiceNotFoundException</tt> with the specified detail message and cause.
     *
     * @param uri the service URI that could not be found
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public ServiceNotFoundException(final URI uri, final String msg, final Throwable cause) {
        super(msg, cause);
        serviceUri = uri;
    }

    /**
     * Get the service URI which could not be found.
     *
     * @return the service URI
     */
    public URI getServiceUri() {
        return serviceUri;
    }

    /**
     * Returns the detail message string of this throwable.
     *
     * @return the detail message string of this throwable
     */
    public String getMessage() {
        return super.getMessage() + ": " + serviceUri;
    }
}
