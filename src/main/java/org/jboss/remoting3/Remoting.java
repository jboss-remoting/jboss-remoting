/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.jboss.remoting3;

import java.io.IOException;
import org.jboss.remoting3.security.RemotingPermission;
import org.xnio.ChannelThreadPool;
import org.xnio.ChannelThreadPools;
import org.xnio.OptionMap;
import org.xnio.ReadChannelThread;
import org.xnio.WriteChannelThread;
import org.xnio.Xnio;

/**
 * The standalone interface into Remoting.  This class contains static methods that are useful to standalone programs
 * for managing endpoints and services in a simple fashion.
 *
 * @apiviz.landmark
 */
public final class Remoting {

    private static final RemotingPermission CREATE_ENDPOINT_PERM = new RemotingPermission("createEndpoint");

    /**
     * Create an endpoint with the given configuration.
     *
     * @param endpointName the name of the endpoint
     * @param xnio the XNIO instance to use
     * @param optionMap the options to configure the endpoint
     * @return the new endpoint
     * @throws IOException if an error occurs
     */
    public static Endpoint createEndpoint(final String endpointName, final Xnio xnio, final OptionMap optionMap) throws IOException {
        if (endpointName == null) {
            throw new IllegalArgumentException("endpointName is null");
        }
        if (optionMap == null) {
            throw new IllegalArgumentException("optionMap is null");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_ENDPOINT_PERM);
        }
        final int readPoolSize = optionMap.get(RemotingOptions.READ_THREAD_POOL_SIZE, 1);
        if (readPoolSize < 1) {
            throw new IllegalArgumentException("Read thread pool must have at least one thread");
        }
        final int writePoolSize = optionMap.get(RemotingOptions.WRITE_THREAD_POOL_SIZE, 1);
        if (writePoolSize < 1) {
            throw new IllegalArgumentException("Write thread pool must have at least one thread");
        }
        boolean ok = false;
        ChannelThreadPool<ReadChannelThread> readPool = null;
        ChannelThreadPool<WriteChannelThread> writePool = null;
        try {
            if (readPoolSize == 1) {
                readPool = ChannelThreadPools.singleton(xnio.createReadChannelThread());
            } else {
                readPool = ChannelThreadPools.createRoundRobinPool();
                ChannelThreadPools.addReadThreadsToPool(xnio, readPool, readPoolSize, optionMap);
            }
            if (writePoolSize == 1) {
                writePool = ChannelThreadPools.singleton(xnio.createWriteChannelThread());
            } else {
                writePool = ChannelThreadPools.createRoundRobinPool();
                ChannelThreadPools.addWriteThreadsToPool(xnio, writePool, writePoolSize, optionMap);
            }
            ok = true;
        } finally {
            if (! ok) {
                if (readPool != null) ChannelThreadPools.shutdown(readPool);
                if (writePool != null) ChannelThreadPools.shutdown(writePool);
            }
        }
        return new EndpointImpl(xnio, readPool, writePool, endpointName, optionMap);
    }

    /**
     * Create a new endpoint with the given configuration.  The XNIO implementation which is visible from the class
     * loader of this class will be used.
     *
     * @param endpointName the name of the endpoint
     * @param optionMap the options to configure the endpoint
     * @return the new endpoint
     * @throws IOException if an error occurs
     */
    public static Endpoint createEndpoint(final String endpointName, final OptionMap optionMap) throws IOException {
        return createEndpoint(endpointName, Xnio.getInstance(Remoting.class.getClassLoader()), optionMap);
    }

    private Remoting() { /* empty */ }
}
