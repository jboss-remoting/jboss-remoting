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
import java.util.concurrent.Executor;

import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.reflect.SunReflectiveCreator;
import org.jboss.remoting3.ClientListener;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.RequestListener;
import org.jboss.remoting3.Endpoint.ServiceBuilder;
import org.jboss.remoting3.samples.socket.server.SocketClientListener;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 14, 2009
 * </p>
 */
public class SocketProtocol
{
   private static MarshallerFactory marshallerFactory;
   private static MarshallingConfiguration marshallingConfiguration;
   

   /**
    * Register ConnectionProvider.
    * This endpoint can be a socket transport client.
    */
   static public <T, I, O> void registerClientTransport(final Endpoint endpoint, final Executor executor, final String host) {
      endpoint.addConnectionProvider("socket", new ConnectionProviderFactory() {
         public ConnectionProvider createInstance(ConnectionProviderContext context) {
            return new SocketConnectionProvider<I, O>(endpoint, executor, host);
         }});
   }


   /**
    * Register ConnectionProvider and start its listening facility.
    * This endpoint can be both a client and server for the socket transport.
    */
   static public <T, I, O> Cancellable registerServerTransport(Endpoint endpoint, Executor executor, final String host, final int port) {
      final SocketConnectionProvider<I, O> connectionProvider = new SocketConnectionProvider<I, O>(endpoint, executor, host);
      endpoint.addConnectionProvider("socket", new ConnectionProviderFactory() {
         public ConnectionProvider createInstance(ConnectionProviderContext context) {
            try {
               connectionProvider.start(context, port);
               return connectionProvider;
            } catch (IOException e) {
               throw new RuntimeException("unable to start SocketServerConnectionProvider", e);
            }
         }
      });

      return new Cancellable() {
         public Cancellable cancel() {
            connectionProvider.stop();
            return IoUtils.nullCancellable();
         }
      };
   }


   /**
    * Register a service with an endpoint.  
    * This endpoint must be acting as a socket transport server.
    */
   @SuppressWarnings("unchecked")
   static public <I, O> void startService(Endpoint endpoint, Executor executor, SocketServiceConfiguration<I, O> socketConfig, final RequestListener<I, O> requestListener) throws IOException {
      String serviceType = socketConfig.getServiceType();
      String groupName = socketConfig.getGroupName();
      ClientListener<I, O> clientListener = new SocketClientListener<I, O>(endpoint, socketConfig, requestListener);
      ServiceBuilder<I, O> sb = (ServiceBuilder<I, O>) endpoint.serviceBuilder();
      sb.setRequestType(socketConfig.getRequestClass());
      sb.setReplyType(socketConfig.getResponseClass());
      sb.setClientListener(clientListener);
      sb.setServiceType(serviceType);
      sb.setGroupName(groupName);
      sb.register();
   }
   
   
   static public <I, O> void initializeMarshalling(Endpoint endpoint, Executor executor) {
      marshallerFactory = Marshalling.getMarshallerFactory("river");
      marshallingConfiguration = new MarshallingConfiguration();
      marshallingConfiguration.setCreator(new SunReflectiveCreator());
      marshallingConfiguration.setObjectTable(new SocketObjectTable<I, O>(endpoint, executor));
   }
   
   
   static public MarshallerFactory getMarshallerFactory() throws IllegalStateException {
      if (marshallerFactory == null) {
         throw new IllegalStateException("marshalling has not been initialized");
      }
      return marshallerFactory;
   }
   
   
   static public MarshallingConfiguration getMarshallingConfiguration() throws IllegalStateException {
      if (marshallingConfiguration == null) {
        throw new IllegalStateException("marshalling has not been initialized");
      }
      return marshallingConfiguration;
   }
}

