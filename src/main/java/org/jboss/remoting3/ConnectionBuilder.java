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

import java.net.SocketAddress;
import java.net.URI;

import javax.security.sasl.SaslClientFactory;

import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
* @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
*/
public final class ConnectionBuilder {

    private final URI uri;
    private boolean immediate;
    private SaslClientFactory saslClientFactory;
    private AuthenticationContext authenticationContext;
    private SocketAddress bindAddress;

    ConnectionBuilder(final URI uri) {
        this.uri = uri;
    }

    public ConnectionBuilder setImmediate(final boolean immediate) {
        this.immediate = immediate;
        return this;
    }

    public void setSaslClientFactory(final SaslClientFactory saslClientFactory) {
        Assert.checkNotNullParam("saslClientFactory", saslClientFactory);
        this.saslClientFactory = saslClientFactory;
    }

    public void setAuthenticationContext(final AuthenticationContext authenticationContext) {
        Assert.checkNotNullParam("authenticationContext", authenticationContext);
        this.authenticationContext = authenticationContext;
    }

    public void setBindAddress(final SocketAddress bindAddress) {
        Assert.checkNotNullParam("bindAddress", bindAddress);
        this.bindAddress = bindAddress;
    }

    URI getUri() {
        return uri;
    }

    boolean isImmediate() {
        return immediate;
    }

    SaslClientFactory getSaslClientFactory() {
        return saslClientFactory;
    }

    AuthenticationContext getAuthenticationContext() {
        return authenticationContext;
    }

    SocketAddress getBindAddress() {
        return bindAddress;
    }
}
