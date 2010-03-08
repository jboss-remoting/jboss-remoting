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
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.samples.socket.SocketHandleableCloseable;
import org.jboss.remoting3.samples.socket.SocketProtocol;
import org.jboss.remoting3.samples.socket.server.SocketServerRequestHandler;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.LocalRequestHandler;
import org.jboss.remoting3.spi.RemoteRequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Result;
import org.jboss.xnio.log.Logger;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Nov 10, 2009
 * </p>
 */
public class SocketRequestHandlerConnector<I, O> extends AbstractHandleableCloseable<SocketHandleableCloseable> implements RequestHandlerConnector, SocketHandleableCloseable, Serializable {
   private static final long serialVersionUID = 37933691697892626L;
   private static final Logger log = Logger.getLogger(SocketRequestHandlerConnector.class);
   
   private String callbackHost;
   private transient RequestHandlerServer requestHandlerServer;
   private int callbackPort;
   private SocketClientRequestHandler socketClientRequestHandler;
   
   public SocketRequestHandlerConnector() {
      // ???
      super(new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()));
   }
   
   public SocketRequestHandlerConnector(Executor executor, LocalRequestHandler localRequestHandler, String callbackHost) throws IOException {
      super(executor);
      this.callbackHost = callbackHost;
      requestHandlerServer = new RequestHandlerServer(localRequestHandler, callbackHost);
      callbackPort = requestHandlerServer.getLocalPort();
      requestHandlerServer.start();
   }

   public Cancellable createRequestHandler(Result<RemoteRequestHandler> result) throws SecurityException {
      if (socketClientRequestHandler != null) {
         throw new SecurityException(this + ": a SocketClientRequestHandler has already been created");
      }
      
      try
      {
         Socket socket = new Socket(callbackHost, callbackPort);
         log.info("server created callback Socket");
         MarshallerFactory factory = SocketProtocol.getMarshallerFactory();
         MarshallingConfiguration configuration = SocketProtocol.getMarshallingConfiguration();
         final Marshaller marshaller = factory.createMarshaller(configuration);
         final Unmarshaller unmarshaller = factory.createUnmarshaller(configuration);
         marshaller.start(Marshalling.createByteOutput(socket.getOutputStream()));
         marshaller.flush();
         unmarshaller.start(Marshalling.createByteInput(socket.getInputStream()));
         socketClientRequestHandler = new SocketClientRequestHandler(getExecutor(), marshaller, unmarshaller);
         result.setResult(socketClientRequestHandler);
         registerCloseHandler(socketClientRequestHandler);
         registerCloseHandler(marshaller);
         registerCloseHandler(unmarshaller);
         registerCloseHandler(socket);
      } catch (IOException e) {
         result.setException(e);
      }
      
      return IoUtils.nullCancellable();
   }

   void registerCloseHandler(final Object o) {
      try {
         final Method close = o.getClass().getMethod("close");
         addCloseHandler(new CloseHandler<Object>() {
            public void handleClose(Object closed) {
               try {
                  close.invoke(o);
               } catch (Exception e) {
                  log.warn(this + " unable to close " + o, e);
               }
            }     
         });
      } catch (Exception e) {
         throw new RuntimeException(this + " got object without close method: " + o);
      }
   }

   static class RequestHandlerServer extends Thread {
      private LocalRequestHandler localRequestHandler;
      private ServerSocket serverSocket;
      private Socket socket;
      private SocketServerRequestHandler socketServerRequestHandler;

      RequestHandlerServer(LocalRequestHandler localRequestHandler, String localHost) throws IOException {
         this.localRequestHandler = localRequestHandler;
         serverSocket = new ServerSocket();
         serverSocket.bind(new InetSocketAddress(localHost, 0));
      }

      public void run() {
         try
         {
            socket = serverSocket.accept();
            log.info("client created callback Socket");
            socketServerRequestHandler = new SocketServerRequestHandler(localRequestHandler, socket);
            socketServerRequestHandler.start();
         } catch (IOException e) {
            log.error(this + " unable to accept a new Socket", e);  
         } finally {
            try {
               serverSocket.close();
            } catch (IOException e) {
               log.warn(this + " unable to close ServerSocket " + serverSocket, e);
            }
            serverSocket = null;
         }
      }

      int getLocalPort() {
         return serverSocket.getLocalPort();
      }

      void close() {
         try {
            if (serverSocket != null) {
               serverSocket.close();
            }
         } catch (IOException e) {
            log.warn(this + " unable to close ServerSocket " + serverSocket, e);
         } finally {
            try {
               if (socket != null) {
                  socket.close();
               }
            } catch (IOException e) {
               log.warn(this + " unable to close Socket " + socket, e);
            } finally {
               try {
                  if (socketServerRequestHandler != null) {
                     socketServerRequestHandler.close();
                  }
               } catch (IOException e) {
                  log.warn(this + " unable to close SocketServerRequestHandler " + socketServerRequestHandler, e);
               }
            }
         }
      }
   }
}
