/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.remoting3.samples.socket.server;

import java.io.IOException;

import org.jboss.marshalling.Marshaller;
import org.jboss.remoting3.spi.RemoteReplyHandler;
import org.jboss.xnio.log.Logger;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 16, 2009
 * </p>
 */
public class SocketServerReplyHandler implements RemoteReplyHandler
{
   private static final Logger log = Logger.getLogger(SocketServerReplyHandler.class);
   private Marshaller marshaller;
   
   public SocketServerReplyHandler(Marshaller marshaller) {
      this.marshaller = marshaller;
   }
   
   public void handleCancellation() throws IOException {
   }

   public void handleException(IOException exception) throws IOException {
      marshaller.writeObject(exception);
      marshaller.flush();
   }

   public void handleReply(Object reply) throws IOException {
      log.info(this + " handling reply: " + reply);
      marshaller.writeObject(reply);
      marshaller.flush();
      log.info(this + " handled reply: " + reply);
   }
}

