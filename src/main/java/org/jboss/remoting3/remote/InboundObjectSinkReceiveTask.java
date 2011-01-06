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

import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.NioByteInput;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.stream.ObjectSink;
import org.jboss.xnio.IoUtils;

final class InboundObjectSinkReceiveTask implements Runnable {

    private final NioByteInput byteInput;
    private final InboundStream inboundStream;
    private final RemoteConnectionHandler connectionHandler;
    private final ObjectSink objectSink;

    InboundObjectSinkReceiveTask(final NioByteInput byteInput, final InboundStream inboundStream, final RemoteConnectionHandler connectionHandler, final ObjectSink objectSink) {
        this.byteInput = byteInput;
        this.inboundStream = inboundStream;
        this.connectionHandler = connectionHandler;
        this.objectSink = objectSink;
    }

    @SuppressWarnings({ "unchecked" })
    public void run() {
        final MarshallerFactory marshallerFactory = connectionHandler.getMarshallerFactory();
        final MarshallingConfiguration configuration = connectionHandler.getMarshallingConfiguration();
        final ObjectSink objectSink = this.objectSink;
        boolean ok = false;
        try {
            final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(configuration);
            try {
                unmarshaller.start(byteInput);
                for (;;) {
                    final int cmd = unmarshaller.read();
                    switch (cmd) {
                        case RemoteProtocol.OSINK_OBJECT: {
                            final Object obj = unmarshaller.readObject();
                            objectSink.accept(obj);
                            break;
                        }
                        case RemoteProtocol.OSINK_FLUSH: {
                            objectSink.flush();
                            break;
                        }
                        case RemoteProtocol.OSINK_CLOSE:
                        case -1: {
                            objectSink.close();
                            ok = true;
                            return;
                        }
                        default: {
                            // no idea, just close everything and send an async exception
                            return;
                        }
                    }
                }
            } finally {
                IoUtils.safeClose(unmarshaller);
            }
        } catch (Exception e) {
            // todo log it
        } finally {
            IoUtils.safeClose(objectSink);
            if (! ok) inboundStream.sendAsyncException();
        }
    }
}
