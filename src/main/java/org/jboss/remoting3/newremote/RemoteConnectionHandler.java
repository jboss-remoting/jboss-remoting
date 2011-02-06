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
import java.util.Random;
import org.jboss.marshalling.util.IntKeyMap;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.xnio.Cancellable;
import org.xnio.OptionMap;
import org.xnio.Result;

final class RemoteConnectionHandler extends AbstractHandleableCloseable<RemoteConnectionHandler> implements ConnectionHandler {

    static final int LENGTH_PLACEHOLDER = 0;

    private final ConnectionHandlerContext connectionContext;
    private final RemoteConnection remoteConnection;
    private final Random random = new Random();

    /**
     * Channels initiated locally.  Local channel IDs are read with a "0" MSB and written with a "1" MSB.
     */
    private final IntKeyMap<RemoteChannel> localChannels = new IntKeyMap<RemoteChannel>();
    /**
     * Channels initiated remotely.  Remote channel IDs are read with a "1" MSB and written with a "0" MSB.
     */
    private final IntKeyMap<RemoteChannel> remoteChannels = new IntKeyMap<RemoteChannel>();

    RemoteConnectionHandler(final ConnectionHandlerContext connectionContext, final RemoteConnection remoteConnection) {
        super(connectionContext.getConnectionProviderContext().getExecutor());
        this.connectionContext = connectionContext;
        this.remoteConnection = remoteConnection;
    }

    public Cancellable open(final String serviceType, final String groupName, final Result<Channel> result, final ClassLoader classLoader, final OptionMap optionMap) {
        return null;
    }

    protected void closeAction() throws IOException {
        try {
            remoteConnection.close();
        } finally {
        }
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
}
