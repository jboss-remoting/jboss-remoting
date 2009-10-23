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

package org.jboss.remoting3;

import org.jboss.xnio.IoFuture;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.xnio.Cancellable;
import java.io.Serializable;
import java.io.IOException;

final class ClientConnectorImpl<I, O> implements ClientConnector<I, O>, Serializable {

    private static final long serialVersionUID = -263585821458635701L;

    private transient final ClientContext clientContext;

    private final RequestHandlerConnector requestHandlerConnector;
    private final Endpoint endpoint;
    private final Class<I> requestClass;
    private final Class<O> replyClass;

    ClientConnectorImpl(final RequestHandlerConnector requestHandlerConnector, final Endpoint endpoint, final Class<I> requestClass, final Class<O> replyClass, final ClientContext clientContext) {
        this.requestHandlerConnector = requestHandlerConnector;
        this.endpoint = endpoint;
        this.requestClass = requestClass;
        this.replyClass = replyClass;
        this.clientContext = clientContext;
    }

    public IoFuture<? extends Client<I, O>> getFutureClient() throws SecurityException {
        if (clientContext != null) {
            throw new SecurityException("Connector has not been sent");
        }
        return new FutureResult<Client<I, O>, RequestHandler>() {
            private final Cancellable cancellable = requestHandlerConnector.createRequestHandler(getResult());

            protected Client<I, O> translate(final RequestHandler result) throws IOException {
                return endpoint.createClient(result, requestClass, replyClass);
            }

            public IoFuture<Client<I, O>> cancel() {
                cancellable.cancel();
                return super.cancel();
            }
        };
    }

    public ClientContext getClientContext() {
        if (clientContext == null) {
            throw new SecurityException("Connector has already been sent");
        }
        return clientContext;
    }


}
