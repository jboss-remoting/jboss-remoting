/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.remoting3;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.jboss.remoting3.security.RemotingPermission;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

/**
 * A builder for a Remoting endpoint.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EndpointBuilder {
    private String endpointName;
    private XnioWorker xnioWorker;
    private OptionMap xnioWorkerOptions;
    private List<ConnectionBuilder> connectionBuilders;
    private List<ConnectionProviderFactoryBuilder> connectionProviderFactoryBuilders;

    EndpointBuilder() {
    }

    public EndpointBuilder setEndpointName(final String endpointName) {
        this.endpointName = endpointName;
        return this;
    }

    public EndpointBuilder setXnioWorker(final XnioWorker xnioWorker) {
        this.xnioWorker = xnioWorker;
        return this;
    }

    public EndpointBuilder setXnioWorkerOptions(final OptionMap xnioWorkerOptions) {
        this.xnioWorkerOptions = xnioWorkerOptions;
        return this;
    }

    public ConnectionBuilder addConnection(final URI uri) {
        final ConnectionBuilder builder = new ConnectionBuilder(uri);
        if (connectionBuilders == null) {
            connectionBuilders = new ArrayList<>();
            connectionBuilders.add(builder);
        }
        return builder;
    }

    public ConnectionProviderFactoryBuilder addProvider(final String scheme) {
        final ConnectionProviderFactoryBuilder builder = new ConnectionProviderFactoryBuilder(scheme);
        if (connectionProviderFactoryBuilders == null) {
            connectionProviderFactoryBuilders = new ArrayList<>();
            connectionProviderFactoryBuilders.add(builder);
        }
        return builder;
    }

    String getEndpointName() {
        return endpointName;
    }

    XnioWorker getXnioWorker() {
        return xnioWorker;
    }

    OptionMap getXnioWorkerOptions() {
        return xnioWorkerOptions;
    }

    List<ConnectionBuilder> getConnectionBuilders() {
        return connectionBuilders;
    }

    List<ConnectionProviderFactoryBuilder> getConnectionProviderFactoryBuilders() {
        return connectionProviderFactoryBuilders;
    }

    public Endpoint build() throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(RemotingPermission.CREATE_ENDPOINT);
        }
        return EndpointImpl.construct(this);
    }
}
