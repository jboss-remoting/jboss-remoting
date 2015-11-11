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

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;

import javax.net.ssl.SSLSession;

import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

class ConnectionImpl extends AbstractHandleableCloseable<Connection> implements Connection {

    private final Attachments attachments = new Attachments();

    private final ConnectionHandler connectionHandler;
    private final Endpoint endpoint;
    private final URI peerUri;

    ConnectionImpl(final EndpointImpl endpoint, final ConnectionHandlerFactory connectionHandlerFactory, final ConnectionProviderContext connectionProviderContext, final URI peerUri) {
        super(endpoint.getExecutor(), true);
        this.endpoint = endpoint;
        this.peerUri = peerUri;
        connectionHandler = connectionHandlerFactory.createInstance(endpoint.new LocalConnectionContext(connectionProviderContext, this));
    }

    protected void closeAction() throws IOException {
        connectionHandler.closeAsync();
        connectionHandler.addCloseHandler((closed, exception) -> closeComplete());
    }

    public SocketAddress getLocalAddress() {
        return connectionHandler.getLocalAddress();
    }

    public SocketAddress getPeerAddress() {
        return connectionHandler.getPeerAddress();
    }

    ConnectionHandler getConnectionHandler() {
        return connectionHandler;
    }

    @Override
    public SSLSession getSslSession() {
        return connectionHandler.getSslSession();
    }

    public IoFuture<Channel> openChannel(final String serviceType, final OptionMap optionMap) {
        FutureResult<Channel> result = new FutureResult<Channel>(getExecutor());
        result.addCancelHandler(connectionHandler.open(serviceType, result, optionMap));
        return result.getIoFuture();
    }

    public String getRemoteEndpointName() {
        return connectionHandler.getRemoteEndpointName();
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public URI getPeerURI() {
        return peerUri;
    }

    public Attachments getAttachments() {
        return attachments;
    }

    public String toString() {
        return String.format("Remoting connection <%x>", Integer.valueOf(hashCode()));
    }
}
