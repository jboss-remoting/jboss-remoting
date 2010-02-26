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
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.Executor;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.samples.socket.SocketHandleableCloseable;
import org.jboss.remoting3.samples.socket.SocketProtocol;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;
import org.jboss.xnio.log.Logger;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 14, 2009
 * </p>
 */
public class SocketClientConnectionHandler extends AbstractHandleableCloseable<SocketHandleableCloseable> implements ConnectionHandler, SocketHandleableCloseable {
   private static final Logger log = Logger.getLogger("org.jboss.remoting.samples.socket.client.SocketClientConnectionHandler");

   private String remoteHost;
   private int remotePort;
   private String localHost;

   public SocketClientConnectionHandler(URI uri, OptionMap connectOptions, Executor executor, String localHost, int localPort) {
      super(executor);
      this.remoteHost = uri.getHost();
      this.remotePort = uri.getPort();
      this.localHost = localHost;
   }

   public RequestHandlerConnector createConnector(RequestHandler localHandler) {
      try {
         SocketRequestHandlerConnector<?, ?> connector = new SocketRequestHandlerConnector<Object, Object>(getExecutor(), localHandler, localHost);
         registerCloseHandler(connector);
         return connector;
      } catch (IOException e) {
         log.error(this + " unable to create SocketRequestHandlerConnector", e);
         return null;
      } 
   }

   public Cancellable open(final String serviceType, final String groupName, Result<RequestHandler> result) {
      try
      {
         final Socket socket = new Socket(remoteHost, remotePort);
         log.info("client created socket");
         MarshallerFactory factory = SocketProtocol.getMarshallerFactory();
         MarshallingConfiguration configuration = SocketProtocol.getMarshallingConfiguration();
         final Marshaller marshaller = factory.createMarshaller(configuration);
         final Unmarshaller unmarshaller = factory.createUnmarshaller(configuration);
         marshaller.start(Marshalling.createByteOutput(socket.getOutputStream()));
         marshaller.writeUTF(serviceType);
         marshaller.writeUTF(groupName);
         marshaller.flush();
         unmarshaller.start(Marshalling.createByteInput(socket.getInputStream()));
         result.setResult(new SocketClientRequestHandler(getExecutor(), marshaller, unmarshaller));
         registerCloseHandler(socket, marshaller, unmarshaller);
      } catch (IOException e) {
         result.setException(e);
      }
      return IoUtils.nullCancellable();
   }

   protected void registerCloseHandler(final Socket socket, final Marshaller marshaller, final Unmarshaller unmarshaller) {
      addCloseHandler(new CloseHandler<Object>() {
         public void handleClose(Object closed) {
            try {
               marshaller.close();
            } catch (IOException e) {
              log.warn(this + " unable to close marshaller: " + marshaller);
            } finally {
               try {
                  unmarshaller.close();
               } catch (IOException e) {
                  log.warn(this + " unable to close unmarshaller: " + unmarshaller);
               } finally {
                  try {
                     socket.close();
                  } catch (IOException e) {
                     log.warn(this + " unable to close socket: " + socket);
                  }
               }
            }
         }    
      });
   }
   
   protected void registerCloseHandler(final SocketRequestHandlerConnector<?, ?> requestHandlerConnector) {
      addCloseHandler(new CloseHandler<Object>() {
         public void handleClose(Object closed) {
            try {
               requestHandlerConnector.close();
            } catch (IOException e) {
               log.warn(this + " unable to close SocketRequestHandlerConnector: " + requestHandlerConnector);
            }
         }
      });
   }
}
