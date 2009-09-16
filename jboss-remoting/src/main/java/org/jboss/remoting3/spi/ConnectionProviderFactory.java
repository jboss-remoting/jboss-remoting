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

/**
 * A connection provider factory.  Implementations of this interface provide a connection facility for a URI scheme.  An
 * endpoint will call the {@code createInstance()} method with its provider context when instances of this interface
 * are registered on that endpoint.
 */
public interface ConnectionProviderFactory<T> {

    /**
     * Create a provider instance for an endpoint.
     *
     * @param context the provider context
     * @return the provider
     */
    ConnectionProvider<T> createInstance(ConnectionProviderContext context);
}
