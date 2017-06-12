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

import java.util.concurrent.Executor;
import org.jboss.remoting3.Endpoint;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * A context for a connection provider.  This provides additional endpoint methods to connection providers which are not
 * accessible otherwise.
 */
public interface ConnectionProviderContext {

    /**
     * Accept a connection that was received by the corresponding protocol handler.
     *
     * @param connectionHandlerFactory the connection handler factory
     * @param authenticationFactory the SASL authentication factory to use for server-side authentications
     */
    void accept(ConnectionHandlerFactory connectionHandlerFactory, SaslAuthenticationFactory authenticationFactory);

    /**
     * Get the endpoint.
     *
     * @return the endpoint
     */
    Endpoint getEndpoint();

    /**
     * Get the XNIO instance.
     *
     * @return the XNIO instance
     */
    Xnio getXnio();

    /**
     * Get an executor usable for running asynchronous tasks.
     *
     * @return the executor
     */
    Executor getExecutor();

    /**
     * Get the XNIO worker to use for network operations.
     *
     * @return the XNIO worker
     */
    XnioWorker getXnioWorker();

    /**
     * Get the protocol of this connection provider.
     *
     * @return the protocol of this connection provider (not {@code null})
     */
    String getProtocol();
}
