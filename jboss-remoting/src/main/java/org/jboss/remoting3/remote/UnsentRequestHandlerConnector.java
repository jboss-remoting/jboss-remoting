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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.marshalling.util.IntKeyMap;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.Result;

final class UnsentRequestHandlerConnector implements RequestHandlerConnector {

    private static final AtomicIntegerFieldUpdater<UnsentRequestHandlerConnector> sentUpdater = AtomicIntegerFieldUpdater.newUpdater(UnsentRequestHandlerConnector.class, "sent");

    private final int clientId;
    private final RemoteConnectionHandler remoteConnectionHandler;
    private volatile int sent = 0;

    UnsentRequestHandlerConnector(final int clientId, final RemoteConnectionHandler remoteConnectionHandler) {
        this.clientId = clientId;
        this.remoteConnectionHandler = remoteConnectionHandler;
    }

    public Cancellable createRequestHandler(final Result<RequestHandler> result) throws SecurityException {
        throw new SecurityException("Request handler not sent");
    }

    void send() {
        sent = 1;
    }

    boolean isSent() {
        return sent != 0;
    }

    int getClientId() {
        return clientId;
    }

    protected void finalize() throws Throwable {
        if (sentUpdater.compareAndSet(this, 0, 1)) {
            // was not sent...
            final IntKeyMap<InboundClient> inboundClients = remoteConnectionHandler.getInboundClients();
            synchronized (inboundClients) {
                inboundClients.remove(clientId);
            }
        }
        super.finalize();
    }
}
