/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

import org.jboss.remoting3.HandleableCloseable;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * A context for a connection provider.  This provides additional endpoint methods to connection providers which are not
 * accessible otherwise.
 *
 * @remoting.consume
 */
public interface ConnectionProviderContext extends HandleableCloseable<ConnectionProviderContext> {

    /**
     * Get the endpoint's executor.
     *
     * @return the endpoint executor
     */
    Executor getExecutor();

    /**
     * Accept a connection that was received by the corresponding protocol handler.
     *
     * @param connectionHandlerFactory the connection handler factory
     */
    void accept(ConnectionHandlerFactory connectionHandlerFactory);

    /**
     * Get the currently-registered protocol service providers of the given type.
     *
     * @param serviceType the service type
     * @param <T> the type of the provider interface
     * @return the currently-registered providers
     */
    <T> Iterable<Map.Entry<String, T>> getProtocolServiceProviders(ProtocolServiceType<T> serviceType);

    /**
     * Get one registered protocol service provider of the given type and name.  Returns the provider,
     * or {@code null} if none was registered for that name.
     *
     * @param serviceType the service type
     * @param name the provider name
     * @param <T> the type of the provider interface
     * @return the provider, or {@code null} if none was matched
     */
    <T> T getProtocolServiceProvider(ProtocolServiceType<T> serviceType, String name);
}
