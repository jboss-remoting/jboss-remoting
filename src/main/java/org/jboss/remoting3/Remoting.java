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
import java.util.concurrent.Executor;
import org.jboss.remoting3.security.RemotingPermission;
import org.xnio.OptionMap;

/**
 * The standalone interface into Remoting.  This class contains static methods that are useful to standalone programs
 * for managing endpoints and services in a simple fashion.
 *
 * @apiviz.landmark
 */
public final class Remoting {

    private static final RemotingPermission CREATE_ENDPOINT_PERM = new RemotingPermission("createEndpoint");

    /**
     * Create an endpoint configured with the given option map.  The following options are supported:
     * <ul>
     * </ul>
     *
     * @param endpointName the endpoint name
     * @param executor the thread pool to use
     * @param optionMap the endpoint options
     * @return the endpoint
     * @throws IOException if an error occurs
     */
    public static Endpoint createEndpoint(final String endpointName, final Executor executor, final OptionMap optionMap) throws IOException {
        if (endpointName == null) {
            throw new NullPointerException("endpointName is null");
        }
        if (optionMap == null) {
            throw new NullPointerException("optionMap is null");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_ENDPOINT_PERM);
        }
        final Endpoint endpoint = new EndpointImpl(executor, endpointName, optionMap);
        return endpoint;
    }

    private Remoting() { /* empty */ }
}
