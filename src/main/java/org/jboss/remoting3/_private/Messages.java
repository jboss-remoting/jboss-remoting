/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.remoting3._private;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketAddress;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.remoting3.ChannelBusyException;
import org.jboss.remoting3.NotOpenException;
import org.wildfly.security.auth.AuthenticationException;

import static org.jboss.logging.Logger.Level.*;

import javax.security.sasl.SaslException;

/**
 * All messages.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "JBREM")
public interface Messages extends BasicLogger {
    Messages log = Logger.getMessageLogger(Messages.class, "org.jboss.remoting.remote");
    Messages conn = Logger.getMessageLogger(Messages.class, "org.jboss.remoting.remote.connection");
    Messages server = Logger.getMessageLogger(Messages.class, "org.jboss.remoting.remote.server");
    Messages client = Logger.getMessageLogger(Messages.class, "org.jboss.remoting.remote.client");

    @Message(id = 200, value = "Remote connection failed: %s")
    @LogMessage(level = DEBUG)
    void connectionError(IOException cause);

    @Message(id = 201, value = "Received invalid message on %s")
    IOException invalidMessage(Object connection);

    @Message(id = 202, value = "Abrupt close on %s")
    IOException abruptClose(Object connection);

    @LogMessage(level = WARN)
    @Message(id = 203, value = "Message missing protocol byte")
    void bufferUnderflowRaw();

    @LogMessage(level = WARN)
    @Message(id = 204, value = "Buffer underflow parsing message with protocol ID %02x")
    void bufferUnderflow(int id);

    @LogMessage(level = DEBUG)
    @Message(id = 205, value = "Failed to accept a connection: %s")
    void failedToAccept(/* ! @Cause */ Exception e);

    @Message(id = 206, value = "Channel is not open")
    NotOpenException channelNotOpen();

    @Message(id = 207, value = "Failed to send a message (channel is busy)")
    ChannelBusyException channelBusy();

    @Message(id = 208, value = "Write operation interrupted")
    InterruptedIOException writeInterrupted();

    @LogMessage(level = ERROR)
    @Message(id = 209, value = "An exception occurred in a message handler")
    void exceptionInUserHandler(@Cause Throwable throwable);

    // these two are a pair at id = 210

    @LogMessage(level = FATAL)
    @Message(id = 210, value = "Internal Error: received a message with duplicate ID %d from %s")
    void duplicateMessageId(short messageId, SocketAddress peerAddress);

    @Message(/* id = 210, */value = "Internal Error: received a message with a duplicate ID")
    IOException duplicateMessageIdException();

    @Message(id = 211, value = "Invalid XNIO worker; the worker must match the Remoting Endpoint worker")
    IllegalArgumentException invalidWorker();

    // non i18n

    @LogMessage(level = TRACE)
    @Message(value = "Message with unknown protocol ID %d received")
    void unknownProtocolId(int id);

    @LogMessage(level = TRACE)
    @Message(value = "Rejected invalid SASL mechanism %s")
    void rejectedInvalidMechanism(String name);

    // user auth

    @Message(id = 300, value = "Authentication failed due to I/O error")
    AuthenticationException authenticationExceptionIo(@Cause IOException e);

    @Message(id = 301, value = "Mechanism name \"%s\" is too long")
    IOException mechanismNameTooLong(String mechName);

    @Message(id = 302, value = "Authentication message too large")
    IOException authenticationMessageTooLarge();

    @Message(id = 303, value = "Authentication protocol failed (extra response)")
    AuthenticationException authenticationExtraResponse();

    @Message(id = 304, value = "Server rejected authentication")
    AuthenticationException serverRejectedAuthentication();

    @Message(id = 305, value = "Authentication failed (connection closed)")
    AuthenticationException authenticationExceptionClosed();

    @Message(id = 306, value = "Authentication failed (SASL client construction failure)")
    AuthenticationException authenticationNoSaslClient(@Cause SaslException e);

    @Message(id = 307, value = "Authentication interrupted")
    AuthenticationException authenticationInterrupted();

    @Message(id = 308, value = "Authentication failed (no mechanisms left), tried: %s")
    AuthenticationException noAuthMechanismsLeft(String triedStr);

    @Message(id = 309, value = "Authentication not supported for this peer")
    AuthenticationException authenticationNotSupported();
}
