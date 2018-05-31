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
import java.net.InetSocketAddress;
import java.net.URI;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

final class UncloseableEndpoint implements Endpoint {
    private final Endpoint endpoint;

    UncloseableEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public String getName() {
        return endpoint.getName();
    }

    public Registration registerService(final String serviceType, final OpenListener openListener, final OptionMap optionMap) throws ServiceRegistrationException {
        return endpoint.registerService(serviceType, openListener, optionMap);
    }

    public IoFuture<ConnectionPeerIdentity> getConnectedIdentity(final URI destination, final SSLContext sslContext, final AuthenticationConfiguration authenticationConfiguration) {
        return endpoint.getConnectedIdentity(destination, sslContext, authenticationConfiguration);
    }

    public IoFuture<ConnectionPeerIdentity> getConnectedIdentityIfExists(final URI destination, final SSLContext sslContext, final AuthenticationConfiguration authenticationConfiguration) {
        return endpoint.getConnectedIdentityIfExists(destination, sslContext, authenticationConfiguration);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions) {
        return endpoint.connect(destination, connectOptions);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final AuthenticationContext authenticationContext) {
        return endpoint.connect(destination, connectOptions, authenticationContext);
    }

    public IoFuture<Connection> connect(URI destination, InetSocketAddress bindAddress, OptionMap connectOptions, AuthenticationContext authenticationContext) {
        return endpoint.connect(destination, bindAddress, connectOptions, authenticationContext);
    }

    public Registration addConnectionProvider(final String uriScheme, final ConnectionProviderFactory providerFactory, final OptionMap optionMap) throws DuplicateRegistrationException, IOException {
        return endpoint.addConnectionProvider(uriScheme, providerFactory, optionMap);
    }

    public <T> T getConnectionProviderInterface(final String uriScheme, final Class<T> expectedType) throws UnknownURISchemeException, ClassCastException {
        return endpoint.getConnectionProviderInterface(uriScheme, expectedType);
    }

    public boolean isValidUriScheme(final String uriScheme) {
        return endpoint.isValidUriScheme(uriScheme);
    }

    public XnioWorker getXnioWorker() {
        return endpoint.getXnioWorker();
    }

    public Key addCloseHandler(final CloseHandler<? super Endpoint> handler) {
        return endpoint.addCloseHandler((endpoint, ex) -> handler.handleClose(this, ex));
    }

    public boolean isOpen() {
        return endpoint.isOpen();
    }

    public Attachments getAttachments() {
        return endpoint.getAttachments();
    }

    public void awaitClosedUninterruptibly() {
        endpoint.awaitClosedUninterruptibly();
    }

    public void awaitClosed() throws InterruptedException {
        endpoint.awaitClosed();
    }

    public void close() throws IOException {
        throw Assert.unsupported();
    }

    public void closeAsync() {
        throw Assert.unsupported();
    }

    public IoFuture<Connection> connect(final URI destination, final InetSocketAddress bindAddress, final OptionMap connectOptions, final SSLContext sslContext, final AuthenticationConfiguration connectionConfiguration) {
        return endpoint.connect(destination, bindAddress, connectOptions, sslContext, connectionConfiguration);
    }

	public IoFuture<Connection> connect(URI destination, OptionMap connectOptions, CallbackHandler callbackHandler)
			throws IOException {
		return endpoint.connect(destination, connectOptions, callbackHandler);
	}
}
