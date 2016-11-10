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
import java.util.Map;

import javax.security.sasl.SaslClientFactory;

import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Sequence;

/**
 * A builder for a connection definition.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConnectionBuilder {

    private final URI uri;
    private boolean immediate;
    private SaslClientFactory saslClientFactory;
    private AuthenticationContext authenticationContext;
    private SocketAddress bindAddress;
    private String abstractType;
    private String abstractTypeAuthority;
    private final OptionMap.Builder optionMapBuilder = OptionMap.builder();

    ConnectionBuilder(final URI uri) {
        this.uri = uri;
    }

    public ConnectionBuilder setImmediate(final boolean immediate) {
        this.immediate = immediate;
        return this;
    }

    public ConnectionBuilder setSaslClientFactory(final SaslClientFactory saslClientFactory) {
        Assert.checkNotNullParam("saslClientFactory", saslClientFactory);
        this.saslClientFactory = saslClientFactory;
        return this;
    }

    public ConnectionBuilder setAuthenticationContext(final AuthenticationContext authenticationContext) {
        Assert.checkNotNullParam("authenticationContext", authenticationContext);
        this.authenticationContext = authenticationContext;
        return this;
    }

    public ConnectionBuilder setBindAddress(final SocketAddress bindAddress) {
        Assert.checkNotNullParam("bindAddress", bindAddress);
        this.bindAddress = bindAddress;
        return this;
    }

    public ConnectionBuilder setAbstractType(final String abstractType) {
        Assert.checkNotNullParam("abstractType", abstractType);
        this.abstractType = abstractType;
        return this;
    }

    public ConnectionBuilder setAbstractTypeAuthority(final String abstractTypeAuthority) {
        Assert.checkNotNullParam("abstractTypeAuthority", abstractTypeAuthority);
        this.abstractTypeAuthority = abstractTypeAuthority;
        return this;
    }

    public <T> ConnectionBuilder setOption(final Option<T> key, final T value) {
        optionMapBuilder.set(key, value);
        return this;
    }

    public ConnectionBuilder setOption(final Option<Integer> key, final int value) {
        optionMapBuilder.set(key, value);
        return this;
    }

    public ConnectionBuilder setSequenceOption(final Option<Sequence<Integer>> key, final int... values) {
        optionMapBuilder.setSequence(key, values);
        return this;
    }

    public ConnectionBuilder setOption(final Option<Long> key, final long value) {
        optionMapBuilder.set(key, value);
        return this;
    }

    public ConnectionBuilder setSequenceOption(final Option<Sequence<Long>> key, final long... values) {
        optionMapBuilder.setSequence(key, values);
        return this;
    }

    public ConnectionBuilder setOption(final Option<Boolean> key, final boolean value) {
        optionMapBuilder.set(key, value);
        return this;
    }

    public ConnectionBuilder setSequenceOption(final Option<Sequence<Boolean>> key, final boolean... values) {
        optionMapBuilder.setSequence(key, values);
        return this;
    }

    @SafeVarargs
    public final <T> ConnectionBuilder setSequenceOption(final Option<Sequence<T>> key, final T... values) {
        optionMapBuilder.setSequence(key, values);
        return this;
    }

    public ConnectionBuilder addAllOptions(final Map<?, ?> map) throws ClassCastException {
        optionMapBuilder.add(map);
        return this;
    }

    public ConnectionBuilder addAllOptions(final OptionMap optionMap) {
        optionMapBuilder.addAll(optionMap);
        return this;
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

    String getAbstractType() {
        return abstractType;
    }

    String getAbstractTypeAuthority() {
        return abstractTypeAuthority;
    }

    OptionMap getOptions() {
        return optionMapBuilder.getMap();
    }
}
