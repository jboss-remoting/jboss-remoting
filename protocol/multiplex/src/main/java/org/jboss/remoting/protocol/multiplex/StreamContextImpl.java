/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting.protocol.multiplex;

import org.jboss.remoting.spi.stream.StreamContext;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.Configuration;
import org.jboss.marshalling.MarshallerFactory;
import java.util.concurrent.Executor;
import java.io.IOException;

/**
 *
 */
public final class StreamContextImpl implements StreamContext {

    private final Executor executor;
    private final MarshallerFactory marshallerFactory;
    private final Configuration marshallerConfiguration;

    StreamContextImpl(final Executor executor, final MarshallerFactory marshallerFactory, final Configuration marshallerConfiguration) {
        this.executor = executor;
        this.marshallerFactory = marshallerFactory;
        this.marshallerConfiguration = marshallerConfiguration;
    }

    public Executor getExecutor() {
        return executor;
    }

    public Marshaller createMarshaller() throws IOException {
        return marshallerFactory.createMarshaller(marshallerConfiguration);
    }

    public Unmarshaller createUnmarshaller() throws IOException {
        return marshallerFactory.createUnmarshaller(marshallerConfiguration);
    }
}
