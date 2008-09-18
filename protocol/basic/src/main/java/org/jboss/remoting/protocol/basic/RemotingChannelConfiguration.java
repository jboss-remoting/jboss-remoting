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

package org.jboss.remoting.protocol.basic;

import java.util.concurrent.Executor;
import java.nio.ByteBuffer;
import org.jboss.xnio.BufferAllocator;
import org.jboss.marshalling.MarshallerFactory;

/**
 *
 */
public final class RemotingChannelConfiguration {
    private MarshallerFactory marshallerFactory;
    private int linkMetric;
    private Executor executor;
    private ClassLoader classLoader;
    private BufferAllocator<ByteBuffer> allocator;

    public RemotingChannelConfiguration() {
    }

    public MarshallerFactory getMarshallerFactory() {
        return marshallerFactory;
    }

    public void setMarshallerFactory(final MarshallerFactory marshallerFactory) {
        this.marshallerFactory = marshallerFactory;
    }

    public int getLinkMetric() {
        return linkMetric;
    }

    public void setLinkMetric(final int linkMetric) {
        this.linkMetric = linkMetric;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(final ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public BufferAllocator<ByteBuffer> getAllocator() {
        return allocator;
    }

    public void setAllocator(final BufferAllocator<ByteBuffer> allocator) {
        this.allocator = allocator;
    }
}
