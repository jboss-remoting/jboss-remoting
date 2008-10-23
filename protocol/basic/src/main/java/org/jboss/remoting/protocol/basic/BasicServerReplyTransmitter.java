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

package org.jboss.remoting.protocol.basic;

import java.util.concurrent.BlockingQueue;
import org.jboss.marshalling.Marshaller;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.Handle;

/**
 *
 */
final class BasicServerReplyTransmitter implements Runnable {

    private static final Logger log = Logger.getLogger(BasicServerReplyTransmitter.class);

    private final BlockingQueue<FutureBasicReply> replyQueue;
    private final Marshaller marshaller;
    private final StreamChannel streamChannel;
    private final Handle<RequestHandler> requestHandlerHandle;

    public BasicServerReplyTransmitter(final BlockingQueue<FutureBasicReply> replyQueue, final Marshaller marshaller, final StreamChannel streamChannel, final Handle<RequestHandler> requestHandlerHandle) {
        this.replyQueue = replyQueue;
        this.marshaller = marshaller;
        this.streamChannel = streamChannel;
        this.requestHandlerHandle = requestHandlerHandle;
    }

    public void run() {
        try {
            for (;;) {
                final FutureBasicReply futureBasicReply = replyQueue.take();
                OUT: for (;;) switch (futureBasicReply.awaitInterruptibly()) {
                    case DONE: {
                        marshaller.write(1);
                        marshaller.writeObject(futureBasicReply.get());
                        marshaller.flush();
                        break OUT;
                    }
                    case CANCELLED: {
                        marshaller.write(2);
                        marshaller.writeInt(futureBasicReply.id);
                        marshaller.flush();
                        break OUT;
                    }
                    case FAILED: {
                        marshaller.write(3);
                        marshaller.writeObject(futureBasicReply.getException());
                        marshaller.flush();
                        break OUT;
                    }
                    case WAITING: {
                        // spurious wakeup, try again
                        continue;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.trace(e, "Interrupted");
        } catch (Exception e) {
            log.error(e, "Error in reply transmitter");
        } finally {
            IoUtils.safeClose(streamChannel);
            IoUtils.safeClose(requestHandlerHandle);
        }
    }
}
