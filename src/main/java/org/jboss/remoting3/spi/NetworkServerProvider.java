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

package org.jboss.remoting3.spi;

import java.io.IOException;
import java.net.SocketAddress;

import javax.net.ssl.SSLContext;

import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.channels.AcceptingChannel;

/**
 * A provider interface implemented by connection providers which can be connected to across the network.
 */
public interface NetworkServerProvider {

    /**
     * Create a network server.
     *
     * @param bindAddress the address to bind to
     * @param optionMap the server options
     * @param saslAuthenticationFactory the authentication factory
     * @param sslContext the SSL context to use (may be {@code null})
     * @return the server channel
     * @throws IOException if the server could not be created
     */
    AcceptingChannel<StreamConnection> createServer(SocketAddress bindAddress, OptionMap optionMap, SaslAuthenticationFactory saslAuthenticationFactory, SSLContext sslContext) throws IOException;
}
