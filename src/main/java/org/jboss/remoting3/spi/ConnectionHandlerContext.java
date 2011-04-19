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

package org.jboss.remoting3.spi;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.ServiceNotFoundException;

/**
 * The context for connection handlers.  Used to inform the endpoint of incoming events on an established connection.
 */
public interface ConnectionHandlerContext {

    /**
     * Get the connection provider context associated with this connection handler context.
     *
     * @return the connection provider context
     */
    ConnectionProviderContext getConnectionProviderContext();

    /**
     * Open a service.  This method should return immediately.
     *
     * @param newChannel the new incoming channel
     * @param serviceType the service type string
     * @throws ServiceNotFoundException if the service is not found
     */
    void openService(Channel newChannel, String serviceType) throws ServiceNotFoundException;

    /**
     * Indicate that the remote side has terminated the connection, so the local side should be closed as well.
     */
    void remoteClosed();
}
