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
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Cause;
import org.jboss.logging.LogMessage;
import org.jboss.logging.Logger;
import org.jboss.logging.Message;
import org.jboss.logging.MessageLogger;

/**
 * "Remote" protocol logger.  Message codes from 200-299.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "JBREM")
interface RemoteLogger extends BasicLogger {
    RemoteLogger log = Logger.getMessageLogger(RemoteLogger.class, "org.jboss.remoting.remote");

    @Message(id = 200, value = "Remote connection failed")
    @LogMessage(level = Logger.Level.ERROR)
    void connectionError(@Cause IOException cause);

    @Message(id = 201, value = "Received invalid message on %s")
    IOException invalidMessage(RemoteConnection connection);

    @Message(id = 202, value = "Abrupt close on %s")
    IOException abruptClose(RemoteConnection connection);
}
