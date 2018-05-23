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
import java.net.InetSocketAddress;
import java.net.URI;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

/**
 * @author bmaxwell
 * JBEAP-14783 wraper to imitate Remoting 4.0 behavior
 * Wrap endpoint and ignore addConnectionProvider to avoid DuplicateRegistrationException
 */
@Deprecated
public class LegacyEndpoint implements Endpoint {

	private final Endpoint endpoint;

	public LegacyEndpoint(Endpoint endpoint) {
		this.endpoint = endpoint;

		// JBEAP-14783 - add a handler to shutdown the xnio worker otherwise legacy Remoting 4.0 will not stop
		// completely when endpoint is closed as it did in 4.0
		this.endpoint.addCloseHandler(new CloseHandler<Endpoint>() {
			@Override
			public void handleClose(org.jboss.remoting3.Endpoint closed, IOException exception) {
				endpoint.getXnioWorker().shutdown();
			}
		});
	}

	@Override
	public void close() throws IOException {
		this.endpoint.close();
	}

	@Override
	public void awaitClosed() throws InterruptedException {
		this.endpoint.awaitClosed();
	}

	@Override
	public void awaitClosedUninterruptibly() {
		this.endpoint.awaitClosedUninterruptibly();
	}

	@Override
	public void closeAsync() {
		this.endpoint.closeAsync();
	}

	@Override
	public org.jboss.remoting3.HandleableCloseable.Key addCloseHandler(CloseHandler<? super Endpoint> handler) {
		return this.endpoint.addCloseHandler(handler);
	}

	@Override
	public boolean isOpen() {
		return this.endpoint.isOpen();
	}

	@Override
	public Attachments getAttachments() {
		return this.endpoint.getAttachments();
	}

	@Override
	public String getName() {
		return this.endpoint.getName();
	}

	@Override
	public Registration registerService(String serviceType, OpenListener openListener, OptionMap optionMap)
			throws ServiceRegistrationException {
		return this.endpoint.registerService(serviceType, openListener, optionMap);
	}

	@Override
	public IoFuture<ConnectionPeerIdentity> getConnectedIdentity(URI destination, SSLContext sslContext,
			AuthenticationConfiguration authenticationConfiguration) {
		return this.endpoint.getConnectedIdentity(destination, sslContext, authenticationConfiguration);
	}

	@Override
	public IoFuture<ConnectionPeerIdentity> getConnectedIdentityIfExists(URI destination, SSLContext sslContext,
			AuthenticationConfiguration authenticationConfiguration) {
		return this.endpoint.getConnectedIdentityIfExists(destination, sslContext, authenticationConfiguration);
	}

	@Override
	public IoFuture<Connection> connect(URI destination, OptionMap connectOptions) {
		return this.endpoint.connect(destination, connectOptions);
	}

	@Override
	public IoFuture<Connection> connect(URI destination, OptionMap connectOptions,
			AuthenticationContext authenticationContext) {
		return this.endpoint.connect(destination, connectOptions, authenticationContext);
	}

	@Override
	public IoFuture<Connection> connect(URI destination, InetSocketAddress bindAddress, OptionMap connectOptions,
			AuthenticationContext authenticationContext) {
		return this.endpoint.connect(destination, bindAddress, connectOptions, authenticationContext);
	}

	@Override
	public IoFuture<Connection> connect(URI destination, InetSocketAddress bindAddress, OptionMap connectOptions,
			SSLContext sslContext, AuthenticationConfiguration connectionConfiguration) {
		return this.endpoint.connect(destination, bindAddress, connectOptions, sslContext, connectionConfiguration);
	}

	@Override
	public IoFuture<Connection> connect(URI destination, OptionMap connectOptions, CallbackHandler callbackHandler)
			throws IOException {
		return this.endpoint.connect(destination, connectOptions, callbackHandler);
	}

	@Override
	public Registration addConnectionProvider(String uriScheme, ConnectionProviderFactory providerFactory,
			OptionMap optionMap) throws DuplicateRegistrationException, IOException {

		// do nothing to avoid DuplicateRegistrationException
		return null;
	}

	@Override
	public <T> T getConnectionProviderInterface(String uriScheme, Class<T> expectedType)
			throws UnknownURISchemeException, ClassCastException {
		return this.endpoint.getConnectionProviderInterface(uriScheme, expectedType);
	}

	@Override
	public boolean isValidUriScheme(String uriScheme) {
		return this.endpoint.isValidUriScheme(uriScheme);
	}

	@Override
	public XnioWorker getXnioWorker() {
		return this.endpoint.getXnioWorker();
	}
}