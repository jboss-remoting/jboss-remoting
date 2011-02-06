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

package org.jboss.remoting3.rpc;

import java.io.IOException;
import java.io.InputStream;
import org.jboss.remoting3.Channel;

/**
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RPCClientImpl {
    private final Channel channel;
    private final Channel.MessageHandler rpcMessageHandler = new Channel.MessageHandler() {
        public void handleError(final Channel channel, final IOException error) {
        }

        public void handleEnd(final Channel channel) {
        }

        public void handleMessage(final Channel channel, final InputStream message) {
            // do stuff
        }
    };

    public RPCClientImpl(final Channel channel) {
        this.channel = channel;
    }

    public void start() {
        channel.receiveMessage(rpcMessageHandler);
    }
}
