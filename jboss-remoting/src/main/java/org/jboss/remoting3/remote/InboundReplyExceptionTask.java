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
import org.jboss.marshalling.NioByteInput;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.RemoteReplyException;
import org.jboss.remoting3.RemoteRequestException;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.SpiUtils;

final class InboundReplyExceptionTask implements Runnable {

    private final OutboundRequest outboundRequest;
    private RemoteConnectionHandler remoteConnectionHandler;

    InboundReplyExceptionTask(final RemoteConnectionHandler remoteConnectionHandler, final OutboundRequest outboundRequest) {
        this.remoteConnectionHandler = remoteConnectionHandler;
        this.outboundRequest = outboundRequest;
    }

    public void run() {
        final ReplyHandler replyHandler;
        final OutboundRequest outboundRequest = this.outboundRequest;
        final NioByteInput oldByteInput;
        synchronized (outboundRequest) {
            replyHandler = outboundRequest.getInboundReplyHandler();
            oldByteInput = outboundRequest.getByteInput();
        }
        try {
            final Object exception;
            try {
                final RemoteConnectionHandler connectionHandler = remoteConnectionHandler;
                final Unmarshaller unmarshaller = connectionHandler.getMarshallerFactory().createUnmarshaller(connectionHandler.getMarshallingConfiguration());
                unmarshaller.start(outboundRequest.getByteInput());
                exception = unmarshaller.readObject();
            } catch (IOException e) {
                SpiUtils.safeHandleException(replyHandler, e);
                return;
            } catch (Exception e) {
                SpiUtils.safeHandleException(replyHandler, new RemoteRequestException(e));
                return;
            } catch (Error e) {
                SpiUtils.safeHandleException(replyHandler,new RemoteReplyException("Failed to unmarshall exception reply", e));
                throw e;
            }
            RemoteReplyException rre;
            try {
                rre = new RemoteReplyException((Throwable) exception);
            } catch (ClassCastException e) {
                rre = new RemoteReplyException("Failed to unmarshall remote exception reply");
            }
            SpiUtils.safeHandleException(replyHandler, rre);
        } finally {
            if (oldByteInput != null) {
                oldByteInput.pushEof();
            }
        }
    }
}
