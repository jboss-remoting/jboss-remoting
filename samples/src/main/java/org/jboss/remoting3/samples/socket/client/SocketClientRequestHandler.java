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
package org.jboss.remoting3.samples.socket.client;

import java.io.IOException;
import java.util.concurrent.Executor;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 14, 2009
 * </p>
 */
public class SocketClientRequestHandler extends AbstractHandleableCloseable<RequestHandler> implements RequestHandler {
   private static final Logger log = Logger.getLogger("org.jboss.remoting.samples.socket.SocketRequestHandler");

   private Marshaller marshaller;
   private Unmarshaller unmarshaller;

   public SocketClientRequestHandler(Executor executor, Marshaller marshaller, Unmarshaller unmarshaller) {
      super(executor);
      this.marshaller = marshaller;
      this.unmarshaller = unmarshaller;
   }

   public Cancellable receiveRequest(Object request, final ReplyHandler replyHandler) {
      try {
         marshaller.writeObject(request);
         marshaller.flush();
         log.info(this + ": sent request: " + request);
      }
      catch (IOException e) {
         SpiUtils.safeHandleException(replyHandler, e);  
      }

      getExecutor().execute(new Runnable() {
         public void run() {
            try {
               log.info(SocketClientRequestHandler.this + ": waiting for reply");
               Object reply = unmarshaller.readObject();
               log.info(this + ": reply: " + reply);
               SpiUtils.safeHandleReply(replyHandler, reply);
            } catch (ClassNotFoundException e) {
               SpiUtils.safeHandleException(replyHandler, new IOException("Cannot find class: " + e.getMessage(), e));
            } catch (IOException e) {
               SpiUtils.safeHandleException(replyHandler, e);
            }
         }
      });

      return new Cancellable () {
         public Cancellable cancel() {
            log.debug("Closing " + SocketClientRequestHandler.this);
            IoUtils.safeClose(SocketClientRequestHandler.this);
            return this;
         }
      };
   }
}

