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

import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.Handle;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.ChannelOutputStream;
import org.jboss.xnio.channels.ChannelInputStream;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Marshalling;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Executor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Queue;
import java.util.LinkedList;

/**
 * A very basic example protocol.
 */
public final class BasicProtocol {

    private BasicProtocol() {
    }

    public static final void createServer(final Handle<RequestHandler> requestHandlerHandle, final StreamChannel streamChannel, final BasicConfiguration configuration) throws IOException {
        final RequestHandler requestHandler = requestHandlerHandle.getResource();
        final MarshallingConfiguration marshallerConfiguration = configuration.getMarshallingConfiguration();
        final MarshallerFactory marshallerFactory = configuration.getMarshallerFactory();
        final Marshaller marshaller = marshallerFactory.createMarshaller(marshallerConfiguration);
        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallerConfiguration);
        final Executor executor = configuration.getExecutor();
        marshaller.start(Marshalling.createByteOutput(new ChannelOutputStream(streamChannel)));
        unmarshaller.start(Marshalling.createByteInput(new ChannelInputStream(streamChannel)));
        final BlockingQueue<FutureBasicReply> replyQueue = new LinkedBlockingQueue<FutureBasicReply>();
        // todo - handle rejected execution...
        executor.execute(new BasicServerReplyTransmitter(replyQueue, marshaller, streamChannel, requestHandlerHandle));
        // todo - handle rejected execution...
        executor.execute(new BasicServerRequestConsumer(unmarshaller, requestHandler, replyQueue, streamChannel, requestHandlerHandle));
    }

    public static final Handle<RequestHandler> createClient(final StreamChannel streamChannel, final BasicConfiguration configuration) throws IOException {
        final MarshallingConfiguration marshallerConfiguration = configuration.getMarshallingConfiguration();
        final MarshallerFactory marshallerFactory = configuration.getMarshallerFactory();
        final Marshaller marshaller = marshallerFactory.createMarshaller(marshallerConfiguration);
        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallerConfiguration);
        final Executor executor = configuration.getExecutor();
        marshaller.start(Marshalling.createByteOutput(new ChannelOutputStream(streamChannel)));
        unmarshaller.start(Marshalling.createByteInput(new ChannelInputStream(streamChannel)));
        final Lock reqLock = new ReentrantLock();
        final Queue<ReplyHandler> replyQueue = new LinkedList<ReplyHandler>();
        // todo - handle rejected execution...
        executor.execute(new BasicHandlerReplyConsumer(unmarshaller, streamChannel, reqLock, replyQueue));
        return new BasicRequestHandler(reqLock, marshaller, replyQueue, streamChannel, executor).getHandle();
    }
}
