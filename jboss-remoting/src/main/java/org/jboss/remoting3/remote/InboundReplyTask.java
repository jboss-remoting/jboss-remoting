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
import org.jboss.remoting3.RemoteRequestException;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

final class InboundReplyTask implements Runnable {

    private final OutboundRequest outboundRequest;
    private RemoteConnectionHandler remoteConnectionHandler;

    private static final Logger log = Loggers.main;

    InboundReplyTask(final RemoteConnectionHandler remoteConnectionHandler, final OutboundRequest outboundRequest) {
        this.remoteConnectionHandler = remoteConnectionHandler;
        this.outboundRequest = outboundRequest;
    }

    public void run() {
        final ReplyHandler replyHandler;
        final OutboundRequest outboundRequest = this.outboundRequest;
        synchronized (outboundRequest) {
            replyHandler = outboundRequest.getInboundReplyHandler();
        }
        final Object reply;
        try {
            final RemoteConnectionHandler connectionHandler = remoteConnectionHandler;
            final Unmarshaller unmarshaller = connectionHandler.getMarshallerFactory().createUnmarshaller(connectionHandler.getMarshallingConfiguration());
            try {
                log.trace("Unmarshalling inbound reply");
                unmarshaller.start(outboundRequest.getByteInput());
                final RemoteConnectionHandler old = RemoteConnectionHandler.setCurrent(connectionHandler);
                try {
                    reply = unmarshaller.readObject();
                    unmarshaller.close();
                } finally {
                    RemoteConnectionHandler.setCurrent(old);
                }
                log.trace("Unmarshalled inbound reply %s", reply);
            } finally {
                IoUtils.safeClose(unmarshaller);
            }
        } catch (IOException e) {
            log.trace(e, "Unmarshalling inbound reply failed");
            SpiUtils.safeHandleException(replyHandler, e);
            return;
        } catch (Exception e) {
            log.trace(e, "Unmarshalling inbound reply failed");
            SpiUtils.safeHandleException(replyHandler, new RemoteRequestException(e));
            return;
        } catch (Error e) {
            log.trace(e, "Unmarshalling inbound reply failed");
            SpiUtils.safeHandleException(replyHandler, new RemoteRequestException(e));
            throw e;
        }
        SpiUtils.safeHandleReply(replyHandler, reply);
    }
}
