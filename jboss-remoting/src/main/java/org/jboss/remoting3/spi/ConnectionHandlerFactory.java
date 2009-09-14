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

/**
 * A factory which creates the connection handler for a connection.  Instances of this interface are used only
 * one time to create the single handler instance to correspond to the given local handler.
 *
 * @remoting.implement
 */
public interface ConnectionHandlerFactory {

    /**
     * Create a connection handler instance.  The provided connection handler is the handler for the next hop of
     * the local connection; typically this will be the endpoint loopback connection but it may not be.
     *
     * @param localConnectionHandler the local connection handler for incoming requests
     * @return the connection handler for outgoing requests
     */
    ConnectionHandler createInstance(ConnectionHandler localConnectionHandler);
}
