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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;

import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.reflect.SunReflectiveCreator;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.samples.socket.SocketHandleableCloseable;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.LocalRequestHandler;
import org.jboss.remoting3.spi.RemoteRequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;
import org.jboss.xnio.log.Logger;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 16, 2009
 * </p>
 */
public class SocketServerConnectionHandler<I, O> extends AbstractHandleableCloseable<SocketHandleableCloseable> implements ConnectionHandler, SocketHandleableCloseable {
   private static final Logger log = Logger.getLogger("org.jboss.remoting.samples.socket.server.SocketServerAcceptor");

   private Endpoint endpoint;
   private ConnectionHandlerContext connectionHandlerContext;
   private String host;
   private int port;
   private MarshallingConfiguration marshallingConfig;
   private boolean running;

   public SocketServerConnectionHandler(Endpoint endpoint, Executor executor, ConnectionHandlerContext connectionHandlerContext, String host, int port) {
      super(executor);
      this.endpoint = endpoint;
      this.host = host;
      this.port = port;
      this.connectionHandlerContext = connectionHandlerContext;
      marshallingConfig = new MarshallingConfiguration();
      marshallingConfig.setCreator(new SunReflectiveCreator());
   }

   public void start() throws IOException {
      running = true;
      final ServerSocket ss = new ServerSocket(port, 200, InetAddress.getByName(host));
      new Thread() {
         public void run() {
            while (running) {
               try {
                  Socket socket = ss.accept();
                  log.info("server created socket");
                  SocketServerRequestHandler requestHandler = new SocketServerRequestHandler(endpoint, socket, connectionHandlerContext);
                  registerServerSocketCloseHandler(requestHandler);
                  requestHandler.start();
               }
               catch (IOException e) {
                  log.error("Error handling new connection", e);
               }
            }
            try {
               ss.close();
            } catch (IOException e) {
               log.warn("Error closing ServerSocket: " + ss);
            }
         }
      }.start();
   }


   public void stop() {
      running = false;
      try {
         close();
      } catch (IOException e) {
         log.warn(this + " unable to close");
      }
   }

   @Override
   public RequestHandlerConnector createConnector(LocalRequestHandler localHandler) {
      return null;
   }

   @Override
   public Cancellable open(final String serviceType, final String groupName, Result<RemoteRequestHandler> result, final ClassLoader classLoader, final OptionMap optionMap) {
      return null;
   }

   protected void registerServerSocketCloseHandler(final SocketServerRequestHandler requestHandler) {
      addCloseHandler(new CloseHandler<Object>() {
         public void handleClose(Object closed) {
            try {
               requestHandler.close();
            } catch (IOException e) {
               log.warn("unable to close SocketServerRequestHandler: " + requestHandler);
            }
         }    
      });
   }
}

