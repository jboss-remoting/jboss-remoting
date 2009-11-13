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
package org.jboss.remoting3.samples.socket;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import org.jboss.remoting3.Client;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.RemoteExecutionException;
import org.jboss.remoting3.RequestContext;
import org.jboss.remoting3.RequestListener;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.log.Logger;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Nov 9, 2009
 * </p>
 */
public class SocketRequestListenerProxy<I, O> implements RequestListener<I, O>, Serializable {
   private static final long serialVersionUID = -5260475991325355302L;
   private static Logger log = Logger.getLogger(SocketRequestListenerProxy.class);
   
   private Connection connection;
   private Client<I, O> client;   
   
   public SocketRequestListenerProxy(Endpoint endpoint, SocketServiceConfiguration<I, O> config) throws IOException {
      URI uri;
      try {
         uri = new URI("socket://" + config.getHost() + ":" + config.getPort());
      } catch (URISyntaxException e) {
         throw new IOException(e.getCause());
      }
      connection = getFutureResult(endpoint.connect(uri, OptionMap.EMPTY), "couldn't create Connection");
      client = getFutureResult(connection.openClient(config.getServiceType(), config.getGroupName(), config.getRequestClass(), config.getResponseClass()), "couldn't create Client");
   }

   @Override
   public void handleClose() {
      if (connection != null) {
         try {
            connection.close();
         } catch (IOException e) {
            log.info(this + " unable to close connection " + connection);
         }
      }
      if (client != null) {
         try {
            client.close();
         } catch (IOException e) {
            log.info(this + " unable to close client " + client);
         }
      }
   }
   
   @Override
   public void handleRequest(RequestContext<O> context, I request) throws RemoteExecutionException {
      try {
         O reply = client.invoke(request);
         context.sendReply(reply);
      } catch (CancellationException e) {
         try {
            context.sendCancelled();
         } catch (IllegalStateException e1) {
            throw new RemoteExecutionException(e1);
         } catch (IOException e1) {
            throw new RemoteExecutionException(e1);
         }
      } catch (IOException e) {
         try {
            context.sendFailure(e.getMessage(), e);
         } catch (IllegalStateException e1) {
            throw new RemoteExecutionException(e1);
         } catch (IOException e1) {
            throw new RemoteExecutionException(e1);
         }
      }
   }
   
   static <T> T getFutureResult(IoFuture<T> future, String errorMessage) throws IOException {
      switch (future.await(5000, TimeUnit.MILLISECONDS)) {
         case DONE: {
            return future.get();
         }
         case FAILED: {
            throw future.getException();
         }
         default: {
            throw new IOException("unexpeced future state: " + future);
         }
      }
   }
}