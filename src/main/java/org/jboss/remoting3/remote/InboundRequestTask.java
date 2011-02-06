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

import java.io.IOException;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.util.IntKeyMap;
import org.jboss.remoting3.RemoteRequestException;
import org.jboss.remoting3.spi.SpiUtils;
import org.xnio.IoUtils;
import org.jboss.logging.Logger;

final class InboundRequestTask implements Runnable {

    private final InboundRequest inboundRequest;
    private final int rid;
    private final int cid;
    private RemoteConnectionHandler remoteConnectionHandler;

    private static final Logger log = Loggers.main;

    InboundRequestTask(final RemoteConnectionHandler remoteConnectionHandler, final InboundRequest inboundRequest, final int rid, final int cid) {
        this.remoteConnectionHandler = remoteConnectionHandler;
        this.inboundRequest = inboundRequest;
        this.rid = rid;
        this.cid = cid;
    }

    public void run() {
        final OutboundReplyHandler replyHandler;
        final InboundRequest inboundRequest = this.inboundRequest;
        synchronized (inboundRequest) {
            inboundRequest.setReplyHandler(replyHandler = new OutboundReplyHandler(inboundRequest, rid));
        }
        final Object request;
        try {
            final Unmarshaller unmarshaller = remoteConnectionHandler.getMarshallerFactory().createUnmarshaller(remoteConnectionHandler.getMarshallingConfiguration());
            try {
                log.trace("Unmarshalling inbound request");
                unmarshaller.start(inboundRequest.getByteInput());
                final RemoteConnectionHandler old = RemoteConnectionHandler.setCurrent(remoteConnectionHandler);
                try {
                    request = unmarshaller.readObject();
                    unmarshaller.close();
                } finally {
                    RemoteConnectionHandler.setCurrent(old);
                }
                log.trace("Unmarshalled inbound request %s", request);
            } finally {
                IoUtils.safeClose(unmarshaller);
            }
        } catch (IOException e) {
            log.trace(e, "Unmarshalling inbound request failed");
            SpiUtils.safeHandleException(replyHandler, e);
            return;
        } catch (Exception e) {
            log.trace(e, "Unmarshalling inbound request failed");
            SpiUtils.safeHandleException(replyHandler, new RemoteRequestException(e));
            return;
        } catch (Error e) {
            log.trace(e, "Unmarshalling inbound request failed");
            SpiUtils.safeHandleException(replyHandler, new RemoteRequestException(e));
            throw e;
        }
        final InboundClient inboundClient;
        final IntKeyMap<InboundClient> inboundClients = remoteConnectionHandler.getInboundClients();
        synchronized (inboundClients) {
            inboundClient = inboundClients.get(cid);
        }
        synchronized (inboundRequest) {
            inboundRequest.setCancellable(inboundClient.getHandler().receiveRequest(request, replyHandler));
        }
    }
}
