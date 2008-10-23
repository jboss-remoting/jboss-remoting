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

package org.jboss.remoting;

import org.jboss.remoting.spi.RequestHandlerSource;

/**
 * A listener for watching service registrations on an endpoint.
 */
public interface ServiceListener {

    /**
     * Receive notification that a local service was added.  To receive a notification when it is closed, register a
     * close handler on the provided {@code requestHandlerSource} parameter.
     *
     * @param listenerHandle the handle to this listener
     * @param serviceType the service type string
     * @param groupName the group name string
     * @param requestHandlerSource the request handler source
     */
    void localServiceCreated(SimpleCloseable listenerHandle, String serviceType, String groupName, RequestHandlerSource requestHandlerSource);

    /**
     * Receive notification that a remote service was registered.  To receive a notification when it is unregistered, register a
     * close handler on the provided {@code handle} parameter.
     *
     * @param listenerHandle the handle to this listener
     * @param endpointName the remote endpoint name
     * @param serviceType the service type string
     * @param groupName the group name string
     * @param metric the metric value
     * @param requestHandlerSource the request handler source
     * @param handle the handle to the registration
     */
    void remoteServiceRegistered(SimpleCloseable listenerHandle, String endpointName, String serviceType, String groupName, int metric, RequestHandlerSource requestHandlerSource, SimpleCloseable handle);
}
