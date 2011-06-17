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
import java.net.SocketAddress;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.xnio.OptionMap;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * A provider interface implemented by connection providers which can be connected to across the network.
 */
public interface NetworkServerProvider {

    /**
     * Create a network server.
     *
     * @param bindAddress the address to bind to
     * @param optionMap the server options
     * @param authenticationProvider the authentication provider
     * @return the server channel
     * @throws IOException if the server could not be created
     */
    AcceptingChannel<? extends ConnectedStreamChannel> createServer(SocketAddress bindAddress, OptionMap optionMap, ServerAuthenticationProvider authenticationProvider) throws IOException;
}
