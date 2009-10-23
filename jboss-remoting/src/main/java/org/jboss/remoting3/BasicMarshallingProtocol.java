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

import org.jboss.remoting3.spi.MarshallingProtocol;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.reflect.SunReflectiveCreator;
import org.jboss.xnio.Pool;
import org.jboss.xnio.OptionMap;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.io.IOException;

/**
 * A marshalling protocol which wraps a {@link org.jboss.marshalling.MarshallerFactory}.
 */
public final class BasicMarshallingProtocol implements MarshallingProtocol {

    private final MarshallerFactory marshallerFactory;
    private final int version;

    private static final SunReflectiveCreator creator = AccessController.doPrivileged(new PrivilegedAction<SunReflectiveCreator>() {
        public SunReflectiveCreator run() {
            return new SunReflectiveCreator();
        }
    });

    /**
     * Construct a new instance.
     *
     * @param marshallerFactory the marshaller factory to use
     * @param version the wire protocol version to specify
     */
    public BasicMarshallingProtocol(final MarshallerFactory marshallerFactory, final int version) {
        // todo: security check?
        this.marshallerFactory = marshallerFactory;
        this.version = version;
    }

    /** {@inheritDoc} */
    public Pool<Unmarshaller> getUnmarshallerPool(final Configuration configuration) {
        final MarshallingConfiguration config = buildConfig(configuration);
        return new Pool<Unmarshaller>() {
            public Unmarshaller allocate() {
                try {
                    return marshallerFactory.createUnmarshaller(config);
                } catch (IOException e) {
                    // todo log
                    return null;
                }
            }

            public void free(final Unmarshaller resource) throws IllegalArgumentException {
            }

            public void discard(final Unmarshaller resource) {
            }
        };
    }

    /** {@inheritDoc} */
    public Pool<Marshaller> getMarshallerPool(final Configuration configuration) {
        final MarshallingConfiguration config = buildConfig(configuration);
        return new Pool<Marshaller>() {
            public Marshaller allocate() {
                try {
                    return marshallerFactory.createMarshaller(config);
                } catch (IOException e) {
                    // todo log
                    return null;
                }
            }

            public void free(final Marshaller resource) throws IllegalArgumentException {
            }

            public void discard(final Marshaller resource) {
            }
        };
    }

    private MarshallingConfiguration buildConfig(final Configuration configuration) {
        final MarshallingConfiguration config = new MarshallingConfiguration();
        config.setCreator(creator);
        config.setStreamHeader(Marshalling.nullStreamHeader());
        config.setClassExternalizerFactory(configuration.getUserExternalizerFactory());
        config.setClassTable(configuration.getUserClassTable());
        config.setClassResolver(configuration.getUserClassResolver());
        config.setObjectResolver(configuration.getUserObjectResolver());
        config.setObjectTable(configuration.getUserObjectTable());
        final OptionMap optionMap = configuration.getOptionMap();
        config.setBufferSize(optionMap.get(Options.BUFFER_SIZE, 512));
        config.setClassCount(optionMap.get(Options.CLASS_COUNT, 64));
        config.setInstanceCount(optionMap.get(Options.INSTANCE_COUNT, 256));
        config.setVersion(version);
        return config;
    }
}
