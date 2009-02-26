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

package org.jboss.remoting3.samples.protocol.basic;

import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.Handle;
import org.jboss.remoting3.spi.RemoteRequestContext;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import java.util.concurrent.BlockingQueue;
import java.io.IOException;

/**
 *
 */
final class BasicServerRequestConsumer implements Runnable {

    private static final Logger log = Logger.getLogger(BasicServerRequestConsumer.class);

    private final Unmarshaller unmarshaller;
    private final RequestHandler requestHandler;
    private final BlockingQueue<FutureBasicReply> replyQueue;
    private final StreamChannel streamChannel;
    private final Handle<RequestHandler> requestHandlerHandle;

    public BasicServerRequestConsumer(final Unmarshaller unmarshaller, final RequestHandler requestHandler, final BlockingQueue<FutureBasicReply> replyQueue, final StreamChannel streamChannel, final Handle<RequestHandler> requestHandlerHandle) {
        this.unmarshaller = unmarshaller;
        this.requestHandler = requestHandler;
        this.replyQueue = replyQueue;
        this.streamChannel = streamChannel;
        this.requestHandlerHandle = requestHandlerHandle;
    }

    public void run() {
        try {
            int requestSequence = 0;
            for (;;) {
                final int id = unmarshaller.read();
                switch (id) {
                    case -1: {
                        // done.
                        return;
                    }
                    case 2: {
                        // two-way request
                        final int requestId = requestSequence++;
                        final Object request = unmarshaller.readObject();
                        final FutureBasicReply future = new FutureBasicReply(requestId);
                        replyQueue.add(future);
                        final RemoteRequestContext requestContext = requestHandler.receiveRequest(request, new ReplyHandler() {

                            public void handleReply(final Object reply) {
                                future.setResult(reply);
                            }

                            public void handleException(final IOException exception) {
                                future.setException(exception);
                            }

                            public void handleCancellation() {
                                future.finishCancel();
                            }
                        });
                        future.requestContext = requestContext;
                        break;
                    }
                    case 3: {
                        // cancel request
                        final int requestId = unmarshaller.readInt();
                        // simply iterate over the outstanding requests until we match or are past it...
                        for (FutureBasicReply future : replyQueue) {
                            final int queuedId = future.id;
                            if (queuedId == requestId) {
                                future.cancel();
                                break;
                            } else if (queuedId > requestId) {
                                break;
                            }
                        }
                        break;
                    }
                    default: {
                        // invalid byte
                        throw new IOException("Read an invalid byte from the client");
                    }
                }
            }
        } catch (Exception e) {
            log.error(e, "Connection failed");
        } finally {
            IoUtils.safeClose(streamChannel);
            IoUtils.safeClose(requestHandlerHandle);
        }
    }
}
