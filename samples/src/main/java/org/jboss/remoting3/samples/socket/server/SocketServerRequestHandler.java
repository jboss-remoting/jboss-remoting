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
import java.net.Socket;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.ServiceNotFoundException;
import org.jboss.remoting3.ServiceOpenException;
import org.jboss.remoting3.ServiceURI;
import org.jboss.remoting3.samples.socket.RequestHandlerFuture;
import org.jboss.remoting3.samples.socket.SocketProtocol;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.log.Logger;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 14, 2009
 * </p>
 */
public class SocketServerRequestHandler extends Thread implements RequestHandler {
   private static final Logger log = Logger.getLogger(SocketServerRequestHandler.class);
   private Socket socket;
   private Marshaller marshaller;
   private Unmarshaller unmarshaller;
   private RequestHandler requestHandler;
   private ReplyHandler replyHandler;
   private boolean running;

   /**
    * Calling this constructor creates a 
    * 
    */
   public <I, O> SocketServerRequestHandler(final Endpoint endpoint, Socket socket, ConnectionHandlerContext connectionHandlerContext) {
      try {
         this.socket = socket;
         MarshallerFactory factory = SocketProtocol.getMarshallerFactory();
         MarshallingConfiguration configuration = SocketProtocol.getMarshallingConfiguration();
         marshaller = factory.createMarshaller(configuration);
         marshaller.start(Marshalling.createByteOutput(socket.getOutputStream()));
         marshaller.flush();
         unmarshaller = factory.createUnmarshaller(configuration);
         unmarshaller.start(Marshalling.createByteInput(socket.getInputStream()));
         final String serviceType = unmarshaller.readUTF();
         final String groupName = unmarshaller.readUTF();
         final RequestHandlerFuture requestHandlerFuture = new RequestHandlerFuture();

         ConnectionHandlerContext.ServiceResult serviceResult = new ConnectionHandlerContext.ServiceResult() {
            public void opened(final RequestHandler requestHandler, final OptionMap optionMap) {
               requestHandlerFuture.setResult(requestHandler);
            }
            public void notFound() {
               requestHandlerFuture.setException(new ServiceOpenException("No such service located"));
            }
         };
         final RequestHandler requestHandler = connectionHandlerContext.openService(serviceType, groupName, OptionMap.EMPTY);
         if (requestHandler == null) {
             requestHandlerFuture.setException(new ServiceNotFoundException(ServiceURI.create(serviceType, groupName, null)));
         } else {
             requestHandlerFuture.setResult(requestHandler);
         }
         this.requestHandler = requestHandlerFuture.get();
         if (this.requestHandler == null) {
            throw requestHandlerFuture.getException();
         }
         replyHandler = new SocketServerReplyHandler(marshaller);
      } catch (Exception e) {
         throw new RuntimeException("unable to process socket: " + socket, e);
      }
   }


   public <I, O> SocketServerRequestHandler(RequestHandler localRequestHandler, Socket socket) {
      try {
         this.requestHandler = localRequestHandler;
         this.socket = socket;
         MarshallerFactory factory = SocketProtocol.getMarshallerFactory();
         MarshallingConfiguration configuration = SocketProtocol.getMarshallingConfiguration();
         marshaller = factory.createMarshaller(configuration);
         marshaller.start(Marshalling.createByteOutput(socket.getOutputStream()));
         marshaller.flush();
         unmarshaller = factory.createUnmarshaller(configuration);
         unmarshaller.start(Marshalling.createByteInput(socket.getInputStream()));
         replyHandler = new SocketServerReplyHandler(marshaller);
      } catch (Exception e) {
         throw new RuntimeException("unable to process socket: " + socket, e);
      }
   }


   @Override
   public void run() {
      running = true;
      while (running) {
         Object request;
         try {
            log.info(SocketServerRequestHandler.this + " waiting for next request");
            request = unmarshaller.readObject();
            log.info(SocketServerRequestHandler.this + " got request: " + request);
            requestHandler.receiveRequest(request, replyHandler);
         } catch (ClassNotFoundException e) {
            e.printStackTrace();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
      finishClose();
   }


   @Override
   public void close() throws IOException {
      running = false;
   }


   @Override
   public Cancellable receiveRequest(Object request, ReplyHandler replyHandler) {
      return null;
   }


   @Override
   public org.jboss.remoting3.HandleableCloseable.Key addCloseHandler( CloseHandler<? super RequestHandler> handler) {
      return null;
   }

   public String toString() {
      return "SocketServerRequestHandler[" + super.toString() + "]";
   }

   protected void finishClose() {
      try {
         marshaller.close();
      } catch (IOException e) {
         log.error(this + " unable to close Marshaller " + marshaller, e);
      } finally {
         try {
            unmarshaller.close();
         } catch (IOException e) {
            log.error(this + " unable to close Unmarshaller " + unmarshaller, e);
         } finally {
            try {
               socket.close();
            } catch (IOException e) {
               log.error(this + " unable to close Socket " + socket, e);
            }
         }
      }
   } 
}
