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
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.jboss.remoting3.Client;
import org.jboss.remoting3.ClientConnector;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.RemoteExecutionException;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.RequestContext;
import org.jboss.remoting3.RequestListener;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.log.Logger;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 14, 2009
 * </p>
 */
public class SocketUsageExamples extends TestCase {
   private static final Logger log = Logger.getLogger(SocketUsageExamples.class);
   private static final String DR_NICK_REQUEST = "Hi everybody!";
   private static final String DR_NICK_RESPONSE = "Hi Dr. Nick!";
   private static final String DR_FRANKENSTEIN_REQUEST = "Dr. Frankenstein?";
   private static final String DR_FRANKENSTEIN_RESPONSE = "It's Frankenshteen!";
   private static final String SERVICE_TYPE = "testservice";
   private static final String GROUP_NAME = "testgroup";
   private static final String HOST = "localhost";
   private static final int PORT = 6789;
   private static int portCounter;


   /**
    * Sends a message and gets a result, using Client.send().
    * 
    * @throws Exception
    */
   public void testSocketTransportSend() throws Exception {
      // Start server service.
      log.info("entering " + getName());
      ExecutorService serverExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint serverEndpoint = Remoting.createEndpoint(serverExecutor, "server");
      int serverPort = PORT + portCounter++;
      Cancellable socketServer = SocketProtocol.registerServerTransport(serverEndpoint, serverExecutor, HOST, serverPort);
      SocketServiceConfiguration<String, String> socketServiceConfiguration = new SocketServiceConfiguration<String, String>(SERVICE_TYPE, GROUP_NAME, String.class, String.class, HOST, serverPort);
      SocketProtocol.startService(serverEndpoint, serverExecutor, socketServiceConfiguration, new DrNickRequestListener());

      // Create client and connect to server.
      ExecutorService clientExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint clientEndpoint = Remoting.createEndpoint(serverExecutor, "client");
      SocketProtocol.registerClientTransport(clientEndpoint, clientExecutor, HOST);
      Connection connection = getFutureResult(clientEndpoint.connect(new URI("socket://" + HOST + ":" + serverPort), OptionMap.EMPTY), "couldn't create Connection");
      Client<String, String> client = getFutureResult(connection.openClient(SERVICE_TYPE, GROUP_NAME, String.class, String.class), "couldn't create Client");

      // Send message and get response.
      String response = getFutureResult(client.send(DR_NICK_REQUEST), "couldn't get response");
      assertEquals(DR_NICK_RESPONSE, response);

      // Shut down.
      client.close();
      connection.close();
      clientEndpoint.close();
      socketServer.cancel();
      serverEndpoint.close();
      log.info(getName() + " PASSES");
   }


   /**
    * Sends a message and gets a result, using Client.invoke().
    * 
    * @throws Exception
    */
   public void testSocketTransportInvoke() throws Exception {
      // Start server service.
      log.info("entering " + getName());
      ExecutorService serverExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint serverEndpoint = Remoting.createEndpoint(serverExecutor, "server");
      int serverPort = PORT + portCounter++;
      Cancellable socketServer = SocketProtocol.registerServerTransport(serverEndpoint, serverExecutor, HOST, serverPort);
      SocketServiceConfiguration<String, String> socketServiceConfiguration = new SocketServiceConfiguration<String, String>(SERVICE_TYPE, GROUP_NAME, String.class, String.class, HOST, serverPort);
      SocketProtocol.startService(serverEndpoint, serverExecutor, socketServiceConfiguration, new DrNickRequestListener());

      // Create client and connect to server.
      ExecutorService clientExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint clientEndpoint = Remoting.createEndpoint(serverExecutor, "client");
      SocketProtocol.registerClientTransport(clientEndpoint, clientExecutor, HOST);
      Connection connection = getFutureResult(clientEndpoint.connect(new URI("socket://" + HOST + ":" + serverPort), OptionMap.EMPTY), "couldn't create Connection");
      Client<String, String> client = getFutureResult(connection.openClient(SERVICE_TYPE, GROUP_NAME, String.class, String.class), "couldn't create Client");

      // Send message and get response.
      String response = client.invoke(DR_NICK_REQUEST);
      assertEquals(DR_NICK_RESPONSE, response);

      // Shut down.
      client.close();
      connection.close();
      clientEndpoint.close();
      socketServer.cancel();
      serverEndpoint.close();
      log.info(getName() + " PASSES");
   }


   /**
    * Creates two Endpoints in server mode and sends messages in both directions.
    * 
    * @throws Exception
    */
   public void testSocketTwoWayTransport() throws Exception {
      // Start west coast service.
      log.info("entering " + getName());
      ExecutorService westernExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint westernEndpoint = Remoting.createEndpoint(westernExecutor, "west coast server");
      int westernPort = PORT + portCounter++;
      Cancellable westernServer = SocketProtocol.registerServerTransport(westernEndpoint, westernExecutor, HOST, westernPort);
      SocketServiceConfiguration<String, String> westernServiceConfiguration = new SocketServiceConfiguration<String, String>(SERVICE_TYPE, GROUP_NAME, String.class, String.class, HOST, westernPort);
      SocketProtocol.startService(westernEndpoint, westernExecutor, westernServiceConfiguration, new DrNickRequestListener());

      // Start east coast service.
      ExecutorService easternExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint easternEndpoint = Remoting.createEndpoint(easternExecutor, "west coast server");
      int easternPort = PORT + portCounter++;
      Cancellable easternServer = SocketProtocol.registerServerTransport(easternEndpoint, easternExecutor, HOST, easternPort);
      SocketServiceConfiguration<String, String> easternServiceConfiguration = new SocketServiceConfiguration<String, String>(SERVICE_TYPE, GROUP_NAME, String.class, String.class, HOST, easternPort);
      SocketProtocol.startService(easternEndpoint, easternExecutor, easternServiceConfiguration, new DrNickRequestListener());

      // Send message east to west and get response.
      Connection eastWestConnection = getFutureResult(easternEndpoint.connect(new URI("socket://" + HOST + ":" + westernPort), OptionMap.EMPTY), "couldn't create Connection");
      Client<String, String> eastWestClient = getFutureResult(eastWestConnection.openClient(SERVICE_TYPE, GROUP_NAME, String.class, String.class), "couldn't create Client");
      String response = getFutureResult(eastWestClient.send(DR_NICK_REQUEST), "couldn't get response from west coast");
      assertEquals(DR_NICK_RESPONSE, response);
      log.info("EAST to WEST PASSES");
      response = getFutureResult(eastWestClient.send(DR_NICK_REQUEST), "couldn't get response from west coast");
      assertEquals(DR_NICK_RESPONSE, response);
      log.info("EAST to WEST PASSES AGAIN");

      // Send message east to west and get response.
      Connection westEastConnection = getFutureResult(westernEndpoint.connect(new URI("socket://" + HOST + ":" + easternPort), OptionMap.EMPTY), "couldn't create Connection");
      Client<String, String> westEastClient = getFutureResult(westEastConnection.openClient(SERVICE_TYPE, GROUP_NAME, String.class, String.class), "couldn't create Client");
      response = getFutureResult(westEastClient.send(DR_NICK_REQUEST), "couldn't get response from east coast");
      assertEquals(DR_NICK_RESPONSE, response);
      log.info("WEST to EAST PASSES");
      response = getFutureResult(westEastClient.send(DR_NICK_REQUEST), "couldn't get response from east coast");
      assertEquals(DR_NICK_RESPONSE, response);
      log.info("WEST to EAST PASSES AGAIN");

      // Shut down.
      eastWestClient.close();
      eastWestConnection.close();
      westEastClient.close();
      westEastConnection.close();
      easternServer.cancel();
      westernServer.cancel();
      westernEndpoint.close();
      easternEndpoint.close();
      log.info(getName() + " PASSES");
   }


   /**
    * Registers two services on a remote Endpoint and sends a message to both of them.
    * 
    * @throws Exception
    */
   public void testSocketMultipleServices() throws Exception {
      // Create server.
      log.info("entering " + getName());
      ExecutorService serverExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint serverEndpoint = Remoting.createEndpoint(serverExecutor, "server");
      int serverPort = PORT + portCounter++;
      Cancellable socketServer = SocketProtocol.registerServerTransport(serverEndpoint, serverExecutor, HOST, serverPort);

      // Start first service.
      SocketServiceConfiguration<String, String> socketServiceConfiguration = new SocketServiceConfiguration<String, String>(SERVICE_TYPE + "1", GROUP_NAME, String.class, String.class, HOST, serverPort);
      SocketProtocol.startService(serverEndpoint, serverExecutor, socketServiceConfiguration, new DrNickRequestListener());

      // Start second service.
      socketServiceConfiguration = new SocketServiceConfiguration<String, String>(SERVICE_TYPE + "2", GROUP_NAME, String.class, String.class, HOST, serverPort);
      SocketProtocol.startService(serverEndpoint, serverExecutor, socketServiceConfiguration, new DrFrankensteinRequestListener());

      // Create client endpoint and get connection.
      ExecutorService clientExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint clientEndpoint = Remoting.createEndpoint(serverExecutor, "client");
      SocketProtocol.registerClientTransport(clientEndpoint, clientExecutor, HOST);
      Connection connection = getFutureResult(clientEndpoint.connect(new URI("socket://" + HOST + ":" + serverPort), OptionMap.EMPTY), "couldn't create Connection");

      // Send message to first service and get response.
      Client<String, String> client1 = getFutureResult(connection.openClient(SERVICE_TYPE + "1", GROUP_NAME, String.class, String.class), "couldn't create Client");
      String response = getFutureResult(client1.send(DR_NICK_REQUEST), "couldn't get response");
      assertEquals(DR_NICK_RESPONSE, response);

      // Send message to second service and get response.
      Client<String, String> client2 = getFutureResult(connection.openClient(SERVICE_TYPE + "2", GROUP_NAME, String.class, String.class), "couldn't create Client");
      response = getFutureResult(client2.send(DR_FRANKENSTEIN_REQUEST), "couldn't get response");
      assertEquals(DR_FRANKENSTEIN_RESPONSE, response);

      // Shut down.
      client1.close();
      client2.close();
      connection.close();
      clientEndpoint.close();
      socketServer.cancel();
      serverEndpoint.close();
      log.info(getName() + " PASSES");
   }


   /**
    * Sends url for local service and gets callbacks from a remote service.
    * 
    * @throws Exception
    */
   @SuppressWarnings("unchecked")
   public void testSocketTransportCallbackWithURL() throws Exception {
      // Start remote service.
      log.info("entering " + getName());
      ExecutorService remoteExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint remoteEndpoint = Remoting.createEndpoint(remoteExecutor, "remote endpoint");
      int remotePort = PORT + portCounter++;
      Cancellable remoteServer = SocketProtocol.registerServerTransport(remoteEndpoint, remoteExecutor, HOST, remotePort);
      SocketServiceConfiguration<RequestWrapper, Object> remoteServiceConfiguration = new SocketServiceConfiguration<RequestWrapper, Object>(SERVICE_TYPE + "remote", GROUP_NAME, RequestWrapper.class, Object.class, HOST, remotePort);
      SocketProtocol.startService(remoteEndpoint, remoteExecutor, remoteServiceConfiguration, new CallbackSenderRequestListenerURI(remoteEndpoint));

      // Start local service.
      ExecutorService localExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint localEndpoint = Remoting.createEndpoint(localExecutor, "local server");
      int localPort = PORT + portCounter++;
      Cancellable localServer = SocketProtocol.registerServerTransport(localEndpoint, localExecutor, HOST, localPort);
      SocketServiceConfiguration<Object, Object> localServiceConfiguration = new SocketServiceConfiguration<Object, Object>(SERVICE_TYPE + "local", GROUP_NAME, Object.class, Object.class, HOST, localPort);
      CallbackReceiverRequestListener callbackReceiver = new CallbackReceiverRequestListener();
      SocketProtocol.startService(localEndpoint, localExecutor, localServiceConfiguration, callbackReceiver);

      // Send message to remote server and get callbacks.
      Connection connection = getFutureResult(localEndpoint.connect(new URI("socket://" + HOST + ":" + remotePort), OptionMap.EMPTY), "couldn't create Connection");
      Client<Object, RequestWrapper> client = getFutureResult(connection.openClient(SERVICE_TYPE + "remote", GROUP_NAME, Object.class, RequestWrapper.class), "couldn't create Client");
      RequestWrapper wrapper = new RequestWrapper();
      wrapper.setUrl("socket://" + HOST + ":" + localPort);
      wrapper.setServiceType(SERVICE_TYPE + "local");
      wrapper.setGroupName(GROUP_NAME);
      wrapper.setPayload("callback");
      client.send(wrapper);
      Object callback = callbackReceiver.getNext();
      assertEquals("callback", callback);
      callback = callbackReceiver.getNext();
      assertEquals("callback", callback);
      callback = callbackReceiver.getNext();
      assertEquals("callback", callback);

      // Shut down.
      client.close();
      connection.close();
      localServer.cancel();
      remoteServer.cancel();
      remoteEndpoint.close();
      localEndpoint.close();
      log.info(getName() + " PASSES");
   }


   /**
    * Sends a ClientConnector and gets callbacks.
    * 
    * @throws Exception
    */
   public void testSocketTransportCallbackWithClientConnector() throws Exception {
      // Start remote service.
      log.info("entering " + getName());
      ExecutorService remoteExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint remoteEndpoint = Remoting.createEndpoint(remoteExecutor, "remote endpoint");
      int remotePort = PORT + portCounter++;
      Cancellable remoteServer = SocketProtocol.registerServerTransport(remoteEndpoint, remoteExecutor, HOST, remotePort);
      SocketServiceConfiguration<Object, Object> remoteServiceConfiguration = new SocketServiceConfiguration<Object, Object>(SERVICE_TYPE + "remote", GROUP_NAME, Object.class, Object.class, HOST, remotePort);
      SocketProtocol.startService(remoteEndpoint, remoteExecutor, remoteServiceConfiguration, new CallbackSenderRequestListenerClientConnector());

      // Create local endpoint.
      ExecutorService localExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
      Endpoint localEndpoint = Remoting.createEndpoint(localExecutor, "local server");
      SocketProtocol.registerClientTransport(localEndpoint, localExecutor, HOST);

      // Send ClientConnector to remote server and get callbacks.
      Connection connection = getFutureResult(localEndpoint.connect(new URI("socket://" + HOST + ":" + remotePort), OptionMap.EMPTY), "couldn't create Connection");
      Client<Object, Object> client = getFutureResult(connection.openClient(SERVICE_TYPE + "remote", GROUP_NAME, Object.class, Object.class), "couldn't create Client");
      CallbackReceiverRequestListener callbackReceiver = new CallbackReceiverRequestListener();
      ClientConnector<Object, Object> clientConnector = connection.createClientConnector(callbackReceiver, Object.class, Object.class);
      assertEquals("OK", client.invoke(clientConnector));
      assertEquals("sent callback", client.invoke("send first callback"));
      Object callback = callbackReceiver.getNext();
      assertEquals("callback0", callback);
      assertEquals("sent callback", client.invoke("send second callback"));
      callback = callbackReceiver.getNext();
      assertEquals("callback1", callback);

      // Shut down.
      client.close();
      connection.close();
      remoteServer.cancel();
      remoteEndpoint.close();
      localEndpoint.close();
      log.info(getName() + " PASSES");
   }


   static <T> T getFutureResult(IoFuture<T> future, String errorMessage) throws Exception {
      switch (future.await(5000, TimeUnit.MILLISECONDS)) {
      case DONE: {
         return future.get();
      }
      case FAILED: {
         log.error(errorMessage);
         throw future.getException();
      }
      default: {
         throw new Exception("unexpeced future state: " + future);
      }
      }
   }


   public static class DrNickRequestListener implements RequestListener<String, String> {
      public void handleClose() {  
      }

      public void handleRequest(RequestContext<String> context, String request) throws RemoteExecutionException {
         try {
            log.info(this + ": got request: " + request);
            if (SocketUsageExamples.DR_NICK_REQUEST.equalsIgnoreCase(request))
               context.sendReply(SocketUsageExamples.DR_NICK_RESPONSE);
            else
               context.sendReply(request);
            log.info(this + ": sent response");
         } catch (IllegalStateException e) {
            throw new RemoteExecutionException("Dr. Nick has left the state", e);
         }
         catch (IOException e){
            throw new RemoteExecutionException("Dr. Nick has left the building", e);
         }
      }
   }


   public static class DrFrankensteinRequestListener implements RequestListener<String, String> {
      public void handleClose() {  
      }

      public void handleRequest(RequestContext<String> context, String request) throws RemoteExecutionException {
         try {
            log.info(this + ": got request: " + request);
            if (SocketUsageExamples.DR_FRANKENSTEIN_REQUEST.equalsIgnoreCase(request))
               context.sendReply(SocketUsageExamples.DR_FRANKENSTEIN_RESPONSE);
            else
               context.sendReply(request);
            log.info(this + ": sent response");
         } catch (IllegalStateException e) {
            throw new RemoteExecutionException("Dr. Nick has left the state", e);
         }
         catch (IOException e){
            throw new RemoteExecutionException("Dr. Nick has left the building", e);
         }
      }
   }


   public static class RequestWrapper implements Serializable {
      private static final long serialVersionUID = 1L;
      private String url;
      private String serviceType;
      private String groupName;
      private Object payload;
      public String getUrl() {
         return url;
      }
      public void setUrl(String url) {
         this.url = url;
      }
      public String getServiceType() {
         return serviceType;
      }
      public void setServiceType(String serviceType) {
         this.serviceType = serviceType;
      }
      public String getGroupName() {
         return groupName;
      }
      public void setGroupName(String groupName) {
         this.groupName = groupName;
      }
      public Object getPayload() {
         return payload;
      }
      public void setPayload(Object payload) {
         this.payload = payload;
      }
   }


   static class CallbackSenderRequestListenerURI<I, O> implements RequestListener<RequestWrapper, RequestWrapper> {
      private Endpoint endpoint;

      public CallbackSenderRequestListenerURI(Endpoint endpoint) {
         this.endpoint = endpoint;
      }

      public void handleClose() {  
      }

      public void handleRequest(RequestContext<RequestWrapper> context, RequestWrapper request) throws RemoteExecutionException {
         log.info(this + ": got request: " + request);
         Connection connection = null;
         Client<Object, Object> client = null;
         try {
            connection = getFutureResult(endpoint.connect(new URI(request.getUrl()), OptionMap.EMPTY), "couldn't create Connection");
            client = getFutureResult(connection.openClient(request.getServiceType(), request.getGroupName(), Object.class, Object.class), "couldn't create Client");
            log.info(this + " got client: " + client);
            client.send(request.payload);
            log.info(this + ": sent callback");
            client.send(request.payload);
            log.info(this + ": sent callback");
            client.send(request.payload);
            log.info(this + ": sent callback");
         } catch (IOException e) {
            e.printStackTrace();
         } catch (URISyntaxException e) {
            e.printStackTrace();
         } catch (Exception e) {
            e.printStackTrace();
         }
         finally {
            try {
               client.close();
            } catch (IOException e) {
               e.printStackTrace();
            }
            try {
               connection.close();
            } catch (IOException e) {
               e.printStackTrace();
            }
         }
      }
   }


   static class CallbackSenderRequestListenerClientConnector implements RequestListener<Object, Object> {
      private ClientConnector<Object, Object> clientConnector;
      private Client<Object, Object> client;
      private int counter;

      public void handleClose() {
         if (client != null) {
            try {
               client.close();
               client = null;
            } catch (IOException e) {
               log.warn(this + " unable to close Client " + client);
            }
         }
      }

      @SuppressWarnings("unchecked")
      @Override
      public void handleRequest(RequestContext<Object> context, Object request) throws RemoteExecutionException {
         log.info(this + ": got request: " + request);

         if (request instanceof ClientConnector<?, ?>) {
            clientConnector = (ClientConnector<Object, Object>) request;
            try {
               client = clientConnector.getFutureClient().get();
            } catch (Exception e) {
               log.error("unable to create Client", e);
               fail(context, "unable to create Client", e);
               return;
            }
            answer(context, "OK");
            return;
         }

         if (clientConnector == null) {
            fail(context, "ClientConnector has not been received", null);
            return;
         }

         try {
            client.send("callback" + counter++);
            answer(context, "sent callback");
         } catch (IOException e) {
            fail(context, "unable to send response", e);
         }
      }

      void answer(RequestContext<Object> context, Object response) {
         try {
            context.sendReply(response);
            return;
         } catch (Exception e) {
            try {
               context.sendFailure("unable to respond", e);
               return;
            } catch (Exception e1) {
               log.error(this + " unable to return exception", e1);
               return;
            }
         }
      }

      void fail(RequestContext<Object> context, String response, Throwable t) {
         log.error(response, t);
         try {
            context.sendFailure(response, t);
            return;
         } catch (Exception e) {
            try {
               context.sendFailure("unable to respond", e);
               return;
            } catch (Exception e1) {
               log.error(this + " unable to return exception", e1);
               return;
            }
         }
      }
   }


   public static class CallbackReceiverRequestListener implements RequestListener<Object, Object> {
      private static Logger log = Logger.getLogger(CallbackReceiverRequestListener.class);
      private ArrayList<Object> callbacks = new ArrayList<Object>();

      public void handleClose() {  
      }

      public void handleRequest(RequestContext<Object> context, Object callback) throws RemoteExecutionException {
         log.info(this + ": got callback: " + callback);
         callbacks.add(callback);
         synchronized (callbacks) {
            callbacks.notify();
         }
         try {
            context.sendReply("got callback");
         } catch (Exception e) {
            try {
               context.sendFailure("unable to send reply", e);
            } catch (Exception e1) {
               log.warn("unable to send failure message", e1);
            }
         }
      }

      public Object getNext() {
         if (callbacks.size() == 0) {
            synchronized (callbacks) {
               while (true) {
                  try {
                     log.info(this + " waiting for a callback to return");
                     callbacks.wait();
                     break;
                  } catch (InterruptedException e) {
                     // ignore
                  }
               }
            }
         }
         return callbacks.remove(0);
      }
   }
}

