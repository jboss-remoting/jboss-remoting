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
import java.nio.channels.ClosedChannelException;
import java.util.Random;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.xnio.Cancellable;
import org.xnio.OptionMap;
import org.xnio.Result;

final class RemoteConnectionHandler implements ConnectionHandler {

    static final int LENGTH_PLACEHOLDER = 0;

    private final ConnectionHandlerContext connectionContext;
    private final RemoteConnection remoteConnection;
    private final Random random = new Random();

    /**
     * Channels.  Remote channel IDs are read with a "1" MSB and written with a "0" MSB.
     * Local channel IDs are read with a "0" MSB and written with a "1" MSB.  Channel IDs here
     * are stored from the "read" perspective.  Remote channels "1", Local channels "0" MSB.
     */
    private final UnlockedReadIntIndexHashMap<RemoteConnectionChannel> channels = new UnlockedReadIntIndexHashMap<RemoteConnectionChannel>(RemoteConnectionChannel.INDEXER);

    RemoteConnectionHandler(final ConnectionHandlerContext connectionContext, final RemoteConnection remoteConnection) {
        this.connectionContext = connectionContext;
        this.remoteConnection = remoteConnection;
    }

    public Cancellable open(final String serviceType, final String groupName, final Result<Channel> result, final ClassLoader classLoader, final OptionMap optionMap) {
        return null;
    }

    public void close() throws IOException {
        remoteConnection.handleException(new ClosedChannelException());
    }

    ConnectionHandlerContext getConnectionContext() {
        return connectionContext;
    }

    Random getRandom() {
        return random;
    }

    RemoteConnection getRemoteConnection() {
        return remoteConnection;
    }

    private static final ThreadLocal<RemoteConnectionHandler> current = new ThreadLocal<RemoteConnectionHandler>();

    static RemoteConnectionHandler getCurrent() {
        return current.get();
    }

    static RemoteConnectionHandler setCurrent(RemoteConnectionHandler newCurrent) {
        final ThreadLocal<RemoteConnectionHandler> current = RemoteConnectionHandler.current;
        try {
            return current.get();
        } finally {
            current.set(newCurrent);
        }
    }

    void addChannel(final RemoteConnectionChannel channel) {
        RemoteConnectionChannel existing = channels.putIfAbsent(channel.getChannelId(), channel);
        if (existing != null) {
            // should not be possible...
            channel.getConnection().handleException(new IOException("Attempted to add an already-existing channel"));
        }
    }

    public RemoteConnectionChannel getChannel(final int id) {
        return channels.get(id);
    }
}
