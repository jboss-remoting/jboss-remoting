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

package org.jboss.remoting3;

/**
 * The type of resource supported by a specific connection manager.
 *
 * @apiviz.excluded
 */
public enum ResourceType {

    /**
     * An unknown resource.  Such a resource cannot be opened by an endpoint.
     */
    UNKNOWN,
    /**
     * A client resource.  Use {@link Endpoint#openClient(java.net.URI, Class, Class) Endpoint.openClient(*)} to open
     * a client resource URI.
     */
    CLIENT,
    /**
     * A client source resource.  Use {@link Endpoint#openClientSource(java.net.URI, Class, Class) Endpoint.openClientSource(*)} to open
     * a client source resource URI.
     */
    CLIENT_SOURCE,
    /**
     * An endpoint resource.  Use {@link Endpoint#openEndpointConnection(java.net.URI) Endpoint.openEndpointConnection(*)} to open
     * an endpoint resource URI.
     */
    ENDPOINT,
}
