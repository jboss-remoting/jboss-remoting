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
import java.util.function.Consumer;

import org.wildfly.security.auth.server.sasl.SaslAuthenticationFactory;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;

/**
 * A provider interface that allows connections that have already been accepted to be converted to remoting
 * connections.
 *
 * @author Stuart Douglas
 */
public interface ExternalConnectionProvider {

    /**
     * Create a network server.
     *
     * @param optionMap              the server options
     * @param saslAuthenticationFactory
     * @return the channel adaptor
     * @throws java.io.IOException if the adaptor could not be created
     */
    Consumer<StreamConnection> createConnectionAdaptor(final OptionMap optionMap, final SaslAuthenticationFactory saslAuthenticationFactory) throws IOException;
}
