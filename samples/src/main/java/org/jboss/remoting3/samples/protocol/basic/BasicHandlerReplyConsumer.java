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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.Queue;
import java.io.IOException;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.remoting3.RemoteExecutionException;
import org.jboss.remoting3.ReplyException;
import org.jboss.remoting3.IndeterminateOutcomeException;

/**
 *
 */
final class BasicHandlerReplyConsumer implements Runnable {

    private static final Logger log = Logger.getLogger(BasicHandlerReplyConsumer.class);

    private final AtomicInteger replySequence;
    private final Unmarshaller unmarshaller;
    private final StreamChannel streamChannel;
    private final Lock reqLock;
    private final Queue<ReplyHandler> replyQueue;

    public BasicHandlerReplyConsumer(final Unmarshaller unmarshaller, final StreamChannel streamChannel, final Lock reqLock, final Queue<ReplyHandler> replyQueue) {
        this.unmarshaller = unmarshaller;
        this.streamChannel = streamChannel;
        this.reqLock = reqLock;
        this.replyQueue = replyQueue;
        replySequence = new AtomicInteger();
    }

    public void run() {
        try {
            for (;;) {
                final int type = unmarshaller.read();
                switch (type) {
                    case -1: {
                        // done.
                        return;
                    }
                    case 1: {
                        // reply - success
                        reqLock.lock();
                        try {
                            replySequence.getAndIncrement();
                            final ReplyHandler replyHandler = replyQueue.remove();
                            final Object reply;
                            try {
                                reply = unmarshaller.readObject();
                            } catch (Exception e) {
                                SpiUtils.safeHandleException(replyHandler, new ReplyException("Failed to read reply from server", e));
                                return;
                            }
                            SpiUtils.safeHandleReply(replyHandler, reply);
                            break;
                        } finally {
                            reqLock.unlock();
                        }
                    }
                    case 2: {
                        // reply - cancelled
                        reqLock.lock();
                        try {
                            final int id = unmarshaller.readInt();
                            if (id != replySequence.getAndIncrement()) {
                                replySequence.decrementAndGet();
                                break;
                            }
                            final ReplyHandler replyHandler = replyQueue.remove();
                            SpiUtils.safeHandleCancellation(replyHandler);
                            break;
                        } finally {
                            reqLock.unlock();
                        }
                    }
                    case 3: {
                        // reply - exception
                        reqLock.lock();
                        try {
                            replySequence.getAndIncrement();
                            final ReplyHandler replyHandler = replyQueue.remove();
                            final Throwable e;
                            try {
                                e = (Throwable) unmarshaller.readObject();
                            } catch (Exception e2) {
                                SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Failed to read exception from server", e2));
                                return;
                            }
                            SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Remote execution failed", e));
                            break;
                        } finally {
                            reqLock.unlock();
                        }
                    }
                    default: {
                        // invalid byte
                        throw new IOException("Read an invalid byte from the server");
                    }
                }
            }
        } catch (Exception e) {
            log.error(e, "Error receiving reply");
        } finally {
            IoUtils.safeClose(streamChannel);
            reqLock.lock();
            try {
                while (replyQueue.size() > 0) {
                    ReplyHandler replyHandler = replyQueue.remove();
                    SpiUtils.safeHandleException(replyHandler, new IndeterminateOutcomeException("Connection terminated; operation outcome unknown"));
                }
            } finally {
                reqLock.unlock();
            }
        }
    }
}
