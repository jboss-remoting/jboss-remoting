/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
package org.jboss.remoting3.test.racecondition;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.junit.After;
import org.junit.Test;
import org.xnio.IoFuture;
import org.xnio.IoFuture.Status;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.CloseableChannel;

/**
 * Tests the cancelation of an opening connection.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public abstract class AbstractCancelOpeningConnectionTest {
    protected Endpoint endpoint;
    protected AcceptingChannel<?> serverAcceptingChannel;

    /**
     * Return the future connection that will be canceled.
     *
     */
    protected abstract IoFuture<Connection> connect() throws Exception;

    @After
    public void afterCancel() throws Exception {
        try {
            for (CloseableChannel channel: OpeningConnectionTestHelper.getChannels()) {
                System.out.println("Making sure channel " + channel + " is closed");
                assertFalse("Channel " + channel + " - " + channel.getClass().getName() + " should be closed", channel.isOpen());
            }
        } finally {
            OpeningConnectionTestHelper.clear();
            endpoint.close();
            serverAcceptingChannel.close();
        }
    }

    protected IoFuture<Connection> cancelFutureConnection() throws Exception {
        // try to retrieve a connection and cancel right away
        final IoFuture<Connection> futureConnection = connect();
        futureConnection.cancel();
        System.out.println("Returning futureconnection "+ futureConnection);
        return futureConnection;
    }

    @Test
    @BMRule(name="before finishConnection", targetClass="org.xnio.nio.NioXnioWorker$1",
            targetMethod="handleEvent",targetLocation="AT INVOKE java.nio.channels.SocketChannel.finishConnect",
            condition="TRUE", action="debug(\"attempted to connect, waiting for cancel\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding with attempt to finish connect after future has been canceled\")")
    public void cancelBeforeSocketChannelFinishConnection() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.CANCELLED, canceledConnection.getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertFalse(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }

    @Test
    @BMRule(name="after future.setResult", targetClass="org.xnio.nio.NioXnioWorker$1",
            targetMethod="handleEvent",targetLocation="AFTER INVOKE org.xnio.FutureResult.setResult",
            condition="TRUE", action="debug(\"managed to set ConnectedStreamChannel result, waiting for cancel\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding with open listener\")")
    public void cancelAfterSetConnectedStreamChannelResult() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.CANCELLED, canceledConnection.getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertFalse(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }


    @Test
    @BMRule(name="before FutureResult.addCancelHandler", targetClass="org.jboss.remoting3.remote.RemoteConnectionProvider$2",
            targetMethod="handleEvent", targetLocation="AT INVOKE  org.xnio.FutureResult.addCancelHandler",
            condition="TRUE", action="debug(\"at RemoteConnectionProvider.openListener.handleEvent, waiting for cancel before adding cancel handler\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding add cancel handler\")")
    public void cancelBeforeConnectionProviderAddingCancelHandler() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.CANCELLED, canceledConnection.getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertFalse(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }

    @Test
    @BMRule(name="before FramedMessageChannel.isOpen", targetClass="org.jboss.remoting3.remote.RemoteConnectionProvider$2",
            targetMethod="handleEvent", targetLocation="AT INVOKE  org.xnio.channels.FramedMessageChannel.isOpen",
            condition="TRUE", action="debug(\"at RemoteConnectionProvider.openListener.handleEvent, waiting for cancel before checking if channel is open\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding with isOpen\")")
    public void cancelBeforeCheckingChannelIsOpen() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.CANCELLED, canceledConnection.getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertFalse(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }

    @Test
    @BMRule(name="after FramedMessageChannel.isOpen", targetClass="org.jboss.remoting3.remote.RemoteConnectionProvider$2",
            targetMethod="handleEvent", targetLocation="AFTER INVOKE  org.xnio.channels.FramedMessageChannel.isOpen",
            condition="TRUE", action="debug(\"at RemoteConnectionProvider.openListener.handleEvent, waiting for cancel after checked channel is open\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding with creation of ClientConnectionOpenListener\")")
    public void cancelAfterCheckingChannelIsOpen() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.CANCELLED, canceledConnection.getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertTrue(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }

    @Test
    @BMRule(name="before creation of ClientConnectionOpenListener.Greeting", targetClass="org.jboss.remoting3.remote.ClientConnectionOpenListener$Greeting",
            targetMethod="<init>", condition="TRUE",
            action="debug(\"at new ClientConnectionOpenListener.Greeting, waiting for cancel\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding with creation of ClientConnectionOpenListener.Greeting\")")
    public void cancelBeforeGreetingIsCreated() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.CANCELLED, canceledConnection.getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertTrue(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }

    @Test
    @BMRule(name="after ClientConnectionOpenListener.sendCapRequest", targetClass="org.jboss.remoting3.remote.ClientConnectionOpenListener",
            targetMethod="sendCapRequest", condition="TRUE",
            action="debug(\"Client sent Capabilities request, waiting for cancel\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding\")")
    public void cancelAfterSendCapabilitiesRequest() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.CANCELLED, canceledConnection.getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertTrue(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }

    @Test
    @BMRule(name="after ClientConnectionOpenListener$Capabilities receives response", targetClass="org.jboss.remoting3.remote.ClientConnectionOpenListener$Capabilities$2",
            targetMethod="run", condition="TRUE", targetLocation="AFTER INVOKE org.jboss.remoting3.remote.RemoteConnection.setReadListener",
            action="debug(\"Client received Capabilities response, waiting for cancel\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding\")")
    public void cancelAfterClientReceivedCapabilities() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.CANCELLED, canceledConnection.getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertTrue(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }

    @Test
    @BMRule(name="after ClientConnectionOpenListener$Authentication receives response", targetClass="org.jboss.remoting3.remote.ClientConnectionOpenListener$Authentication$1",
            targetMethod="run", condition="TRUE", targetLocation="AFTER INVOKE org.jboss.remoting3.remote.RemoteConnection.send",
            action="debug(\"Client sent authentication challenge response, waiting for cancel\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding\")")
    public void cancelAfterClientRespondedAuthChallenge() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.CANCELLED, canceledConnection.getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertTrue(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }

    @Test
    @BMRule(name="before ClientConnectionOpenListener$Authentication sets result", targetClass="org.jboss.remoting3.remote.ClientConnectionOpenListener$Authentication$2",
            targetMethod="run", condition="TRUE", targetLocation="AT INVOKE org.xnio.Result.setResult",
            action="debug(\"Client created connection handler, waiting for cancel before setting result\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding\")")
    public void cancelBeforeSettingConnectionHandlerFactoryResult() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.CANCELLED, canceledConnection.getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertTrue(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }

    @Test
    @BMRule(name="after ClientConnectionOpenListener$Authentication sets result", targetClass="org.jboss.remoting3.remote.ClientConnectionOpenListener$Authentication$2",
            targetMethod="run", condition="TRUE", targetLocation="AFTER INVOKE org.xnio.Result.setResult",
            action="debug(\"Client created connection handler, waiting for cancel before setting result\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding\")")
    public void cancelAfterSettingConnectionHandlerFactoryResult() throws Exception {
        IoFuture<Connection> canceledConnection = cancelFutureConnection();
        // future connection should be cancelled
        assertSame(Status.DONE, canceledConnection.getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
       canceledConnection.get().closeAsync();
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertTrue(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }
}
