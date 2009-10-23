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

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.ClassTable;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.ClassExternalizerFactory;
import org.jboss.marshalling.ClassResolver;
import org.jboss.marshalling.ObjectResolver;
import org.jboss.xnio.Pool;
import org.jboss.xnio.OptionMap;

/**
 * A registered marshalling protocol.
 *
 * @remoting.implement
 */
public interface MarshallingProtocol {

    /**
     * Get a configured unmarshaller pool.
     *
     * @param configuration the configuration to use
     * @return the pool
     */
    Pool<Unmarshaller> getUnmarshallerPool(Configuration configuration);

    /**
     * Get a configured marshaller pool.
     *
     * @param configuration the configuration to use
     * @return the pool
     */
    Pool<Marshaller> getMarshallerPool(Configuration configuration);

    /**
     * The configuration for a marshalling protocol.
     *
     * @remoting.consume
     */
    interface Configuration {

        /**
         * Get a user class table, if any.
         *
         * @return the user class table or {@code null} if none is configured
         */
        ClassTable getUserClassTable();

        /**
         * Get a user object table, if any.
         *
         * @return the user object table or {@code null} if none is configured
         */
        ObjectTable getUserObjectTable();

        /**
         * Get a user externalizer factory, if any.
         *
         * @return the user externalizer factory
         */
        ClassExternalizerFactory getUserExternalizerFactory();

        /**
         * Get a user class resolver, if any.
         *
         * @return the user class resolver
         */
        ClassResolver getUserClassResolver();

        /**
         * Get a user object resolver, if any.
         *
         * @return the user object resolver
         */
        ObjectResolver getUserObjectResolver();

        /**
         * Get the options to use for this marshaller configuration.
         *
         * @return the options
         */
        OptionMap getOptionMap();
    }
}
