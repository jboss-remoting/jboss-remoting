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
import java.io.InterruptedIOException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Cause;
import org.jboss.logging.LogMessage;
import org.jboss.logging.Logger;
import org.jboss.logging.Message;
import org.jboss.logging.MessageLogger;
import org.jboss.remoting3.ChannelBusyException;
import org.jboss.remoting3.NotOpenException;

import static org.jboss.logging.Logger.Level.*;

/**
 * "Remote" protocol logger.  Message codes from 200-269.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "JBREM")
interface RemoteLogger extends BasicLogger {
    RemoteLogger log = Logger.getMessageLogger(RemoteLogger.class, "org.jboss.remoting.remote");
    RemoteLogger conn = Logger.getMessageLogger(RemoteLogger.class, "org.jboss.remoting.remote.connection");
    RemoteLogger server = Logger.getMessageLogger(RemoteLogger.class, "org.jboss.remoting.remote.server");
    RemoteLogger client = Logger.getMessageLogger(RemoteLogger.class, "org.jboss.remoting.remote.client");

    @Message(id = 200, value = "Remote connection failed: %s")
    @LogMessage(level = ERROR)
    void connectionError(IOException cause);

    @Message(id = 201, value = "Received invalid message on %s")
    IOException invalidMessage(RemoteConnection connection);

    @Message(id = 202, value = "Abrupt close on %s")
    IOException abruptClose(RemoteConnection connection);

    @LogMessage(level = WARN)
    @Message(id = 203, value = "Message missing protocol byte")
    void bufferUnderflowRaw();

    @LogMessage(level = WARN)
    @Message(id = 204, value = "Buffer underflow parsing message with protocol ID %02x")
    void bufferUnderflow(int id);

    @LogMessage(level = WARN)
    @Message(id = 205, value = "Failed to accept a connection: %s")
    void failedToAccept(/* ! @Cause */ IOException e);

    @Message(id = 206, value = "Channel is not open")
    NotOpenException channelNotOpen();

    @Message(id = 207, value = "Failed to send a message (channel is busy)")
    ChannelBusyException channelBusy();

    @Message(id = 208, value = "Write operation interrupted")
    InterruptedIOException writeInterrupted();

    @LogMessage(level = ERROR)
    @Message(id = 209, value = "An exception occurred in a message handler")
    void exceptionInUserHandler(@Cause Throwable throwable);

    // non i18n
    @LogMessage(level = TRACE)
    @Message(value = "Message with unknown protocol ID %d received")
    void unknownProtocolId(int id);

}
