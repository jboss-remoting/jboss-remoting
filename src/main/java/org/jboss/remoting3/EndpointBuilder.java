/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;

import org.jboss.remoting3.security.RemotingPermission;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * A builder for a Remoting endpoint.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EndpointBuilder {
    private String endpointName;
    private XnioWorker xnioWorker;
    private List<ConnectionProviderFactoryBuilder> connectionProviderFactoryBuilders;
    private XnioWorker.Builder workerBuilder;

    EndpointBuilder() {
    }

    public EndpointBuilder setEndpointName(final String endpointName) {
        this.endpointName = endpointName;
        return this;
    }

    public EndpointBuilder setXnioWorker(final XnioWorker xnioWorker) {
        this.workerBuilder = null;
        this.xnioWorker = xnioWorker;
        return this;
    }

    public XnioWorker.Builder buildXnioWorker(final Xnio xnio) {
        this.xnioWorker = null;
        return this.workerBuilder = xnio.createWorkerBuilder();
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

    XnioWorker.Builder getWorkerBuilder() {
        return workerBuilder;
    }

    List<ConnectionProviderFactoryBuilder> getConnectionProviderFactoryBuilders() {
        return connectionProviderFactoryBuilders;
    }

    public Endpoint build() throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(RemotingPermission.CREATE_ENDPOINT);
        }
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<Endpoint>) () -> EndpointImpl.construct(this));
        } catch (PrivilegedActionException e) {
            throw (IOException) e.getException();
        }
    }
}
