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
import java.net.URI;
import java.util.concurrent.Executor;

import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.samples.socket.client.SocketClientConnectionHandler;
import org.jboss.remoting3.samples.socket.server.SocketServerConnectionHandler;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 14, 2009
 * </p>
 */
public class SocketConnectionProvider<T, I, O> extends AbstractHandleableCloseable<SocketHandleableCloseable> implements ConnectionProvider<T> {
   private Endpoint endpoint;
   private String host;
   private int port;
   private SocketServerConnectionHandler<I, O> connectionHandler;


   public SocketConnectionProvider(Endpoint endpoint, Executor executor, String host) {
      super(executor);
      this.endpoint = endpoint;
      this.host = host;
      SocketProtocol.initializeMarshalling(endpoint, executor);
   }

   public Cancellable connect(final URI uri, final OptionMap connectOptions, Result<ConnectionHandlerFactory> result) throws IllegalArgumentException {
      result.setResult(new ConnectionHandlerFactory() {
         public ConnectionHandler createInstance(ConnectionHandlerContext connectionContext) {
            final ConnectionHandler connectionHandler = new SocketClientConnectionHandler(uri, connectOptions, getExecutor(), host, port);
            registerCloseHandler(connectionHandler);
            return connectionHandler;
         }
      });
      return null;
   }

   public T getProviderInterface() {
      return null;
   }

   public void start(final ConnectionProviderContext context, final int port) throws IOException {
      this.port = port;
      context.accept(new ConnectionHandlerFactory() {
         public ConnectionHandler createInstance(ConnectionHandlerContext connectionContext) {
            connectionHandler = new SocketServerConnectionHandler<I, O>(endpoint, getExecutor(), connectionContext, host, port);
            registerCloseHandler(connectionHandler);
            try {
               connectionHandler.start();
            } catch (IOException e) {
               e.printStackTrace();
            }
            return connectionHandler;
         }});
   }

   public void stop() {
      if (connectionHandler != null) {
         connectionHandler.stop();
      }
   }
   
   protected void registerCloseHandler(final ConnectionHandler connectionHandler) {
      addCloseHandler(new CloseHandler<SocketHandleableCloseable>() {
         public void handleClose(SocketHandleableCloseable closed) {
            IoUtils.safeClose(connectionHandler);
         }
      });
   }
}
