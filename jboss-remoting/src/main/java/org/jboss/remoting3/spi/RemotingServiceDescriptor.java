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

import java.util.Properties;
import java.io.IOException;

/**
 * A descriptor for automatically-discovered remoting service types.  Since instances of this interface are
 * constructed automatically, implementing classes should have a no-arg constructor.
 * <p>
 * To add an automatically-discovered service, create a file called {@code "META-INF/services/org.jboss.remoting3.spi.RemotingServiceDescriptor"}
 * and populate it with the names of classes that implement this interface.
 *
 * @see java.util.ServiceLoader
 */
public interface RemotingServiceDescriptor<T> {

    /**
     * Get the type of service provided by this descriptor.  Only the following types are supported:
     * <ul>
     * <li><code>{@link ConnectionProviderFactory}.class</code> - named connection provider URI scheme</li>
     * <li><code>{@link org.jboss.marshalling.ClassTable ClassTable}.class</code> - named marshalling class table</li>
     * <li><code>{@link org.jboss.marshalling.ObjectTable ObjectTable}.class</code> - named marshalling object table</li>
     * <li><code>{@link org.jboss.marshalling.ClassExternalizerFactory ClassExternalizerFactory}.class</code> - named marshalling externalizer factory</li>
     * <li><code>{@link org.jboss.marshalling.ClassResolver ClassResolver}.class</code> - named marshalling class resolver</li>
     * <li><code>{@link org.jboss.marshalling.ObjectResolver ObjectResolver}.class</code> - named marshalling object resolver</li>
     * </ul>
     * Other types are ignored, allowing new types to be added in the future while maintaining compatibility with
     * older versions.
     *
     * @return the type of remoting service
     */
    Class<T> getType();

    /**
     * Get the name of this service.
     *
     * @return the name
     */
    String getName();

    /**
     * Get the service to associate with the given name.  The given properties were used to configure the endpoint,
     * and may be used to configure additional properties of this provider.
     *
     * @param properties the properties used to configure the endpoint
     * @return the service
     * @throws IOException if the instance could not be produced
     */
    T getService(Properties properties) throws IOException;
}
