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

import org.jboss.cx.remoting.spi.remote.RemoteServiceEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import org.jboss.cx.remoting.spi.remote.Handle;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.CloseHandler;

/**
 *
 */
public final class RemoteServiceEndpointLocalImpl<I, O> implements RemoteServiceEndpoint<I, O> {

    private final EndpointImpl endpointImpl;
    private final RequestListener<I, O> requestListener;

    public RemoteServiceEndpointLocalImpl(final EndpointImpl endpointImpl, final RequestListener<I, O> requestListener) {
        this.endpointImpl = endpointImpl;
        this.requestListener = requestListener;
    }

    public RemoteClientEndpoint<I, O> openClient() throws RemotingException {
        return new RemoteClientEndpointLocalImpl<I, O>(endpointImpl, this, requestListener);
    }

    public Handle<RemoteServiceEndpoint<I, O>> getHandle() throws RemotingException {
        return null;
    }

    public ClientSource<I, O> getClientSource() throws RemotingException {
        return null;
    }

    public void autoClose() {
    }

    public void close() throws RemotingException {
    }

    public void addCloseHandler(final CloseHandler<RemoteServiceEndpoint<I, O>> handler) {
    }
}
