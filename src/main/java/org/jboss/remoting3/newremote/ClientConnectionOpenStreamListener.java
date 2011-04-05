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
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.FramedMessageChannel;

import javax.security.auth.callback.CallbackHandler;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ClientConnectionOpenStreamListener implements ChannelListener<ConnectedStreamChannel> {
    private final Pool<ByteBuffer> channelBufferPool;
    private final Pool<ByteBuffer> messageBufferPool;
    private final OptionMap optionMap;
    private final CallbackHandler callbackHandler;
    private final AccessControlContext accessControlContext;

    ClientConnectionOpenStreamListener(final Pool<ByteBuffer> channelBufferPool, final Pool<ByteBuffer> messageBufferPool, final OptionMap optionMap, final CallbackHandler callbackHandler, final AccessControlContext accessControlContext) {
        this.channelBufferPool = channelBufferPool;
        this.messageBufferPool = messageBufferPool;
        this.optionMap = optionMap;
        this.callbackHandler = callbackHandler;
        this.accessControlContext = accessControlContext;
    }

    public void handleEvent(final ConnectedStreamChannel channel) {
        // Set up the message channel
        try {
            channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
        } catch (IOException e) {
            // ignore
        }
        final FramedMessageChannel messageChannel = new FramedMessageChannel(channel, channelBufferPool.allocate(), channelBufferPool.allocate());
        final RemoteConnection connection = new RemoteConnection(messageBufferPool, messageChannel, optionMap);
        final ClientConnectionOpenListener listener = new ClientConnectionOpenListener(connection, callbackHandler, accessControlContext);
        listener.handleEvent(messageChannel);
    }
}
