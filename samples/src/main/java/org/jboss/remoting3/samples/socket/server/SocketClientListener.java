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
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.remoting3.ClientContext;
import org.jboss.remoting3.ClientListener;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.RequestListener;
import org.jboss.remoting3.samples.socket.SocketServiceConfiguration;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 16, 2009
 * </p>
 */
public class SocketClientListener<I, O> implements ClientListener<I, O> {
   private static ConcurrentHashMap<RequestListener<?, ?>, SocketServiceConfiguration<?, ?>> requestListeners = new ConcurrentHashMap<RequestListener<?, ?>, SocketServiceConfiguration<?, ?>>();
   private RequestListener<I, O> requestListener;
   
   public static SocketServiceConfiguration<?, ?> getRequestListenerInfo(RequestListener<?, ?> requestListener) {
      return requestListeners.get(requestListener);
   }
   
   public SocketClientListener(Endpoint endpoint, SocketServiceConfiguration<I, O> socketConfig, final RequestListener<I, O> requestListener) throws IOException {
      if (requestListeners.containsKey(requestListener)) {
         throw new IOException(requestListener + " is already registered");
      }
      if (requestListeners.values().contains(socketConfig)) {
         throw new IOException("RequestListener with characterized by " + socketConfig + " is already registered");
      }
      requestListeners.put(requestListener, socketConfig);
      endpoint.addCloseHandler(new CloseHandler<Object>() {
         public void handleClose(Object closed) {
            requestListeners.remove(requestListener);
         }
      });
      this.requestListener = requestListener;
   }
   
   public RequestListener<I, O> handleClientOpen(ClientContext clientContext) {
      return requestListener;
   }
}

