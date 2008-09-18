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

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.jboss.remoting.core.EndpointImpl;
import org.jboss.remoting.test.support.LoggingHelper;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.CloseableExecutor;
import org.jboss.xnio.nio.NioXnio;

/**
 *
 */
public final class ConnectionTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testConnection() throws Throwable {
        final String REQUEST = "request";
        final String REPLY = "reply";
        final List<Throwable> problems = Collections.synchronizedList(new LinkedList<Throwable>());
        final CloseableExecutor closeableExecutor = IoUtils.closeableExecutor(Executors.newCachedThreadPool(), 500L, TimeUnit.MILLISECONDS);
        try {
            final BufferAllocator<ByteBuffer> allocator = new BufferAllocator<ByteBuffer>() {
                public ByteBuffer allocate() {
                    return ByteBuffer.allocate(1024);
                }

                public void free(final ByteBuffer buffer) {
                }
            };
            final Xnio xnio = NioXnio.create();
            try {
                final EndpointImpl endpoint = new EndpointImpl();
                endpoint.setExecutor(closeableExecutor);
                endpoint.start();
                try {
                } finally {
                    endpoint.stop();
                }
            } finally {
                IoUtils.safeClose(xnio);
            }
        } finally {
            IoUtils.safeClose(closeableExecutor);
        }
        for (Throwable t : problems) {
            throw t;
        }
    }
}
