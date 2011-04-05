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

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import org.xnio.ChannelListener;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedMessageChannel;

import javax.security.auth.callback.CallbackHandler;

final class ClientConnectionOpenListener implements ChannelListener<ConnectedMessageChannel> {
    private final RemoteConnection connection;
    private final CallbackHandler callbackHandler;
    private final AccessControlContext accessControlContext;

    ClientConnectionOpenListener(final RemoteConnection connection, final CallbackHandler callbackHandler, final AccessControlContext accessControlContext) {
        this.connection = connection;
        this.callbackHandler = callbackHandler;
        this.accessControlContext = accessControlContext;
    }

    public void handleEvent(final ConnectedMessageChannel channel) {
        final Pooled<ByteBuffer> pooled = connection.allocate();
        final ByteBuffer buffer = pooled.getResource();
        // Build initial greeting message
        buffer.put(Protocol.GREETING);
        // version ID
        GreetingUtils.writeByte(buffer, Protocol.GREETING_VERSION, Protocol.VERSION);
        // that's it!
        buffer.flip();
        try {
            connection.send(pooled, null);
        } catch (IOException e) {
            connection.handleException(e);
            pooled.free();
            return;
        }
        connection.setReadListener(new ClientConnectionGreetingListener(connection, callbackHandler, accessControlContext));
    }
}
