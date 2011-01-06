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

import java.io.IOException;
import org.jboss.xnio.OptionMap;

/**
 * The context for connection handlers.  Used to inform the endpoint of incoming events on an established connection.
 *
 * @remoting.consume
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
     * @remoting.nonblocking
     *
     * @param serviceType the service type string
     * @param groupName the group name, or {@code null} for any group name
     * @param optionMap the options to pass to the service
     * @return the new request handler
     * @throws IOException if an error occurs
     */
    LocalRequestHandler openService(String serviceType, String groupName, OptionMap optionMap);

    /**
     * Indicate that the remote side has terminated the connection, so the local side should be closed as well.
     *
     * @remoting.nonblocking
     */
    void remoteClosed();
}
