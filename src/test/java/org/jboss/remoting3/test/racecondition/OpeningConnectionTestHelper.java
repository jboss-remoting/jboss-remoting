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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.xnio.IoFuture;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.CloseableChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * Helper test class for race condition tests that investigate opening connection behavior.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class OpeningConnectionTestHelper extends Helper {
    // list of closeable channels created
    private static List<CloseableChannel> channels = new ArrayList<CloseableChannel>();
    // name that identifies the connected stream channel future
    private static IoFuture<ConnectedStreamChannel> connectedStreamChannelFuture;
    // name that identifies the connection handler factory
    private static IoFuture<ConnectionHandlerFactory> connectionHandlerFactoryFuture;
    private static long lastSelectedTime = 0L;
    private static boolean clientConnectionOpenListenerCreated;

    /**
     * Returns the connected stream channel future created during the test execution.
     */
    public static IoFuture<ConnectedStreamChannel> getConnectedStreamChannelFuture() {
        return connectedStreamChannelFuture;
    }

    /**
     * Returns the connection handler factory future created during the test execution.
     */
    public static IoFuture<ConnectionHandlerFactory> getConnectionHandlerFactoryFuture() {
        return connectionHandlerFactoryFuture;
    }

    /**
     * Returns all channels created during the test execution.
     */
    public static Collection<CloseableChannel> getChannels() {
        synchronized (channels) {
            List<CloseableChannel> allChannels = new ArrayList<CloseableChannel>(channels.size());
            allChannels.addAll(channels);
            return allChannels;
        }
    }

    /**
     * Returns {@code true} if the client connection open listener has been created.
     */
    public static boolean isClientConnectionOpenListenerCreated() {
        return clientConnectionOpenListenerCreated;
    }

    /**
     * Blocks this thread until worker thread is done.
     */
    public static void waitWorkerThreadFinished() throws InterruptedException {
        while (true) {
            if (System.currentTimeMillis() - lastSelectedTime > 300) {
                return;
            } else {
                Thread.sleep(300);
            }
        }
    }

    /**
     * Clears all data recorded so far.
     */
    public static void clear() {
        channels.clear();
        connectedStreamChannelFuture = null;
        connectionHandlerFactoryFuture = null;
        clientConnectionOpenListenerCreated = false;
    }

    /**
     * Creates a FutureConnectionTestHelper for {@code rule}.
     */
    protected OpeningConnectionTestHelper(Rule rule) {
        super(rule);
    }

    /**
     * Tracks the connected stream client channel future, so it can be later {@link #getConnectedStreamChannelFuture()
     * retrieved}.
     *
     * @param future the {@code ConnectedStreamChannel} future}.
     */
    public void trackConnectedStreamChannelFuture(IoFuture<ConnectedStreamChannel> future) {
        if (connectedStreamChannelFuture != null) {
            throw new RuntimeException("Can't track " + future +
                    " as the connected stream channel future; there is already one tracked future: " +
                    connectedStreamChannelFuture);
        }
        connectedStreamChannelFuture = future;
    }

    /**
     * Tracks the connection handler factory future, so it can be later {@link #getConnectionHandlerFactoryFuture()
     * retrieved}.
     *
     * @param future the {@code ConnectionHandlerFactory} future}.
     */
    public void trackConnectionHandlerFactoryFuture(IoFuture<ConnectionHandlerFactory> future) {
        if (connectionHandlerFactoryFuture != null) {
            throw new RuntimeException("Can't track " + future +
                    " as the connection handler factory future; there is already one tracked future: " +
                    connectionHandlerFactoryFuture);
        }
        connectionHandlerFactoryFuture = future;
    }

    /**
     * Tracks {@code channel} so it can be later {@link #getChannels() retrieved}.
     * 
     * @param channel a channel that was created during the test execution
     */
    public void trackChannel(CloseableChannel channel) {
        if (channel instanceof AcceptingChannel) {
            return;
        }
        synchronized (channels) {
            channels.add(channel);
        }
    }

    /**
     * Notifies that the {@link org.jboss.remoting3.remote.ClientConnectionOpenListener} was created.
     */
    public void notifyClientConnectionOpenListenerCreated() {
        clientConnectionOpenListenerCreated = true;
    }

    /**
     * Notifies that the worker thread is awake.
     */
    public void notifyWorkerThreadAwake() {
        lastSelectedTime = System.currentTimeMillis();
    }

 }
