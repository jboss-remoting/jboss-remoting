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

package org.jboss.remoting3.remote;

import org.jboss.marshalling.util.IntKeyMap;
import org.xnio.Cancellable;
import org.xnio.IoUtils;
import org.xnio.Result;

final class ReceivedRequestHandlerConnector implements RequestHandlerConnector {
    private final RemoteConnectionHandler connectionHandler;
    private final int clientId;

    ReceivedRequestHandlerConnector(final RemoteConnectionHandler connectionHandler, final int clientId) {
        this.connectionHandler = connectionHandler;
        this.clientId = clientId;
    }

    public Cancellable createRequestHandler(final Result<RemoteRequestHandler> result) throws SecurityException {
        final OutboundClient client = new OutboundClient(connectionHandler, clientId, result, "anonymous", "anonymous");
        final IntKeyMap<OutboundClient> outboundClients = connectionHandler.getOutboundClients();
        synchronized (outboundClients) {
            outboundClients.put(clientId, client);
        }
        final OutboundRequestHandler requestHandler = new OutboundRequestHandler(client);
        synchronized (client) {
            client.setState(OutboundClient.State.ESTABLISHED);
            client.setResult(requestHandler);
        }
        result.setResult(requestHandler);
        return IoUtils.nullCancellable();
    }
}
