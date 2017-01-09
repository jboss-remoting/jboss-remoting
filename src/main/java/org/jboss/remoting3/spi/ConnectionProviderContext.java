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
