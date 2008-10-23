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

import java.util.concurrent.Executor;
import java.nio.ByteBuffer;
import org.jboss.xnio.BufferAllocator;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Configuration;

/**
 * A configuration object for the multiplex protocol.
 */
public final class MultiplexConfiguration {
    private MarshallerFactory marshallerFactory;
    private Configuration marshallingConfiguration;
    private int linkMetric;
    private Executor executor;
    private BufferAllocator<ByteBuffer> allocator;

    /**
     * Construct a new instance.
     */
    public MultiplexConfiguration() {
    }

    /**
     * Get the marshaller factory to use for messages transmitted and received by this multiplex connection.
     *
     * @return the marshaller factory
     */
    public MarshallerFactory getMarshallerFactory() {
        return marshallerFactory;
    }

    /**
     * Set the marshaller factory to use for messages transmitted and received by this multiplex connection.
     *
     * @param marshallerFactory the marshaller factory
     */
    public void setMarshallerFactory(final MarshallerFactory marshallerFactory) {
        this.marshallerFactory = marshallerFactory;
    }

    /**
     * Get the marshaller configuration to pass into the marshaller factory.
     *
     * @return the configuration
     */
    public Configuration getMarshallingConfiguration() {
        return marshallingConfiguration;
    }

    /**
     * Set the marshaller configuration to pass into the marshaller factory.
     *
     * @param marshallingConfiguration the configuration
     */
    public void setMarshallingConfiguration(final Configuration marshallingConfiguration) {
        this.marshallingConfiguration = marshallingConfiguration;
    }

    /**
     * Get the link metric to assign to this multiplex connection.
     *
     * @return the link metric
     */
    public int getLinkMetric() {
        return linkMetric;
    }

    /**
     * Set the link metric to assign to this multiplex connection.
     *
     * @param linkMetric the link metric
     */
    public void setLinkMetric(final int linkMetric) {
        this.linkMetric = linkMetric;
    }

    /**
     * Get the executor to use to execute
     * @return
     */
    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public BufferAllocator<ByteBuffer> getAllocator() {
        return allocator;
    }

    public void setAllocator(final BufferAllocator<ByteBuffer> allocator) {
        this.allocator = allocator;
    }
}
