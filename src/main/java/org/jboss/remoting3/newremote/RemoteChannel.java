/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.newremote;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.Executor;
import org.jboss.marshalling.util.IntKeyMap;
import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.ChannelBusyException;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.xnio.Pooled;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteChannel extends AbstractHandleableCloseable<Channel> implements Channel {

    private final RemoteConnection connection;
    private final int channelId;

    private final IntKeyMap<OutboundMessage> outboundMessages = new IntKeyMap<OutboundMessage>();
    private final IntKeyMap<InboundMessage> inboundMessages = new IntKeyMap<InboundMessage>();
    private final Random random;
    private MessageHandler nextMessageHandler;

    RemoteChannel(final Executor executor, final RemoteConnection connection, final int channelId, final Random random) {
        super(executor);
        this.connection = connection;
        this.channelId = channelId;
        this.random = random;
    }

    public OutputStream writeMessage() throws IOException {
        int tries = 50;
        synchronized (outboundMessages) {
            while (tries > 0) {
                final int id = random.nextInt() & 0xfffe;
                if (! outboundMessages.containsKey(id)) {
                    OutboundMessage message = new OutboundMessage((short) id, this, 3);
                    outboundMessages.put(id, message);
                    return message.getOutputStream();
                }
                tries --;
            }
            throw new ChannelBusyException("Failed to send a message (channel is busy)");
        }
    }

    public void writeShutdown() throws IOException {
    }

    public void receiveMessage(final MessageHandler handler) {
        synchronized (this) {
            if (nextMessageHandler != null) {
                throw new IllegalStateException("Message handler already queued");
            }
            nextMessageHandler = handler;
        }
    }

    public Attachments getAttachments() {
        return null;
    }

    RemoteConnection getConnection() {
        return connection;
    }

    int getChannelId() {
        return channelId;
    }

    void freeOutboundMessage(final short id) {

    }

    void freeInboundMessage(final short id) {
    }

    Pooled<ByteBuffer> allocate(final byte protoId) {
        final Pooled<ByteBuffer> pooled = connection.allocate();
        final ByteBuffer buffer = pooled.getResource();
        buffer.put(protoId);
        buffer.putInt(channelId);
        return pooled;
    }
}
