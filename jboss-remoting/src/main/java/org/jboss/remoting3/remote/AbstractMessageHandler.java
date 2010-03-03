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

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.MessageHandler;

abstract class AbstractMessageHandler implements MessageHandler {
    protected final RemoteConnection remoteConnection;

    protected AbstractMessageHandler(final RemoteConnection remoteConnection) {
        this.remoteConnection = remoteConnection;
    }

    public void handleEof() {
        try {
            remoteConnection.getChannel().shutdownReads();
            return;
        } catch (IOException e) {
            RemoteConnectionHandler.log.trace(e, "Failed to shut down reads for %s", remoteConnection);
            IoUtils.safeClose(remoteConnection);
        }
    }

    public void handleException(final IOException e) {
        if (e instanceof ClosedChannelException) {
            // ignore; just means there was a race.
            return;
        }
        RemoteConnectionHandler.log.trace(e, "Received exception from %s", remoteConnection);
        IoUtils.safeClose(remoteConnection);
    }
}
