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

package org.jboss.remoting.core;

import java.io.IOException;
import org.jboss.remoting.Client;
import org.jboss.remoting.ClientSource;
import org.jboss.remoting.Endpoint;
import org.jboss.remoting.CloseHandler;
import org.jboss.remoting.spi.AbstractHandleableCloseable;
import org.jboss.remoting.spi.Handle;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class ClientSourceImpl<I, O> extends AbstractHandleableCloseable<ClientSource<I, O>> implements ClientSource<I, O> {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.client-source"); 

    private final Handle<RequestHandlerSource> handle;
    private final Endpoint endpoint;
    private final Class<I> requestClass;
    private final Class<O> replyClass;

    private ClientSourceImpl(final Handle<RequestHandlerSource> handle, final EndpointImpl endpoint, final Class<I> requestClass, final Class<O> replyClass) {
        super(endpoint.getExecutor());
        this.handle = handle;
        this.endpoint = endpoint;
        this.requestClass = requestClass;
        this.replyClass = replyClass;
    }

    static <I, O> ClientSourceImpl<I, O> create(final Handle<RequestHandlerSource> handle, final EndpointImpl endpoint, final Class<I> requestClass, final Class<O> replyClass) {
        final ClientSourceImpl<I, O> csi = new ClientSourceImpl<I, O>(handle, endpoint, requestClass, replyClass);
        handle.addCloseHandler(new CloseHandler<Handle<RequestHandlerSource>>() {
            public void handleClose(final Handle<RequestHandlerSource> closed) {
                IoUtils.safeClose(csi);
            }
        });
        return csi;
    }

    protected void closeAction() throws IOException {
        handle.close();
    }

    public Client<I, O> createClient() throws IOException {
        if (! isOpen()) {
            throw new IOException("Client source is not open");
        }
        final Handle<RequestHandler> clientHandle = handle.getResource().createRequestHandler();
        try {
            return endpoint.createClient(clientHandle.getResource(), requestClass, replyClass);
        } finally {
            IoUtils.safeClose(clientHandle);
        }
    }

    public String toString() {
        return "client source instance <" + Integer.toString(hashCode()) + ">";
    }

    Handle<RequestHandlerSource> getRequestHandlerSourceHandle() {
        return handle;
    }

    Class<I> getRequestClass() {
        return requestClass;
    }

    Class<O> getReplyClass() {
        return replyClass;
    }
}
