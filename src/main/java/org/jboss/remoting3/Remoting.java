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
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.remoting3.security.RemotingPermission;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * The standalone interface into Remoting.  This class contains static methods that are useful to standalone programs
 * for managing endpoints and services in a simple fashion.
 *
 * @apiviz.landmark
 */
@Deprecated
public final class Remoting {

    private static final RemotingPermission CREATE_ENDPOINT_PERM = new RemotingPermission("createEndpoint");

    /**
     * Create an endpoint with the given configuration and existing worker.
     *
     * @param endpointName the name of the endpoint
     * @param xnioWorker the XNIO worker instance to use
     * @param optionMap the options to configure the endpoint
     * @return the new endpoint
     * @throws IOException if an error occurs
     */
    public static Endpoint createEndpoint(final String endpointName, final XnioWorker xnioWorker, final OptionMap optionMap) throws IOException {
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
        EndpointBuilder builder = Endpoint.builder();
        builder.setXnioWorker(xnioWorker);
        builder.setEndpointName(endpointName);

        // JBEAP-14783 - legacy endpoint
        return new LegacyEndpoint(builder.build());
    }

    /**
     * Create an endpoint with the given configuration which manages its own worker.
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
        final OptionMap modifiedOptionMap = OptionMap.builder().addAll(optionMap).set(Options.WORKER_NAME, endpointName == null ? "Remoting (anonymous)" : "Remoting \"" + endpointName + "\"").getMap();
        final AtomicReference<Endpoint> endpointRef = new AtomicReference<Endpoint>();
        final XnioWorker xnioWorker = xnio.createWorker(null, modifiedOptionMap, new Runnable() {
            public void run() {
                final Endpoint endpoint = endpointRef.getAndSet(null);
                if (endpoint != null) {
                    ((EndpointImpl)endpoint).closeComplete();
                }
            }
        });

        EndpointBuilder builder = Endpoint.builder();
        builder.setXnioWorker(xnioWorker);
        builder.setEndpointName(endpointName);
        //builder. optionMap
        final Endpoint endpoint = builder.build();
        endpointRef.set(endpoint);

        // JBEAP-14783 - legacy hacks
        return new LegacyEndpoint(endpoint);
    }

    /**
     * Create a new endpoint with the given configuration.  This method (starting with 3.3) will use the class loader
     * of XNIO itself to construct the XNIO implementation.
     *
     * @param endpointName the name of the endpoint
     * @param optionMap the options to configure the endpoint
     * @return the new endpoint
     * @throws IOException if an error occurs
     */
    public static Endpoint createEndpoint(final String endpointName, final OptionMap optionMap) throws IOException {
        return createEndpoint(endpointName, Xnio.getInstance(), optionMap);
    }

    /**
     * Create an anonymous endpoint.
     *
     * @param optionMap the options to configure the endpoint
     * @return the new endpoint
     * @throws IOException if an error occurs
     */
    public static Endpoint createEndpoint(OptionMap optionMap) throws IOException {
        return createEndpoint(null, optionMap);
    }

    /**
     * Create an anonymous endpoint.
     *
     * @return the new endpoint
     * @throws IOException if an error occurs
     */
    public static Endpoint createEndpoint() throws IOException {
        return createEndpoint(null, OptionMap.EMPTY);
    }

    private Remoting() { /* empty */ }
}
