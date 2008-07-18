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

package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteServiceEndpoint;
import org.jboss.cx.remoting.spi.AbstractCloseable;

/**
 *
 */
public final class ClientSourceImpl<I, O> extends AbstractCloseable<ClientSource<I, O>> implements ClientSource<I, O> {

    private final RemoteServiceEndpoint serviceEndpoint;
    private final Endpoint endpoint;

    ClientSourceImpl(final RemoteServiceEndpoint serviceEndpoint, final EndpointImpl endpoint) {
        super(endpoint.getExecutor());
        this.serviceEndpoint = serviceEndpoint;
        this.endpoint = endpoint;
    }

    public Client<I, O> createClient() throws RemotingException {
        if (! isOpen()) {
            throw new RemotingException("Client source is not open");
        }
        final RemoteClientEndpoint clientEndpoint = serviceEndpoint.createClientEndpoint();
        final Client<I, O> client = endpoint.createClient(clientEndpoint);
        clientEndpoint.autoClose();
        return client;
    }
}
