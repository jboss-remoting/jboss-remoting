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

package org.jboss.remoting3;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import junit.framework.TestCase;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class EndpointTestCase extends TestCase {

    private static final Logger log = Logger.getLogger(EndpointTestCase.class);

    public void testCreate() throws Throwable {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final EndpointImpl endpoint = new EndpointImpl(executorService, "foo");
        try {
            endpoint.close();
            executorService.shutdown();
            assertTrue(executorService.awaitTermination(1L, TimeUnit.SECONDS));
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testLocalClientInvoke() throws Throwable {
        final AtomicBoolean clientEndpointClosed = new AtomicBoolean(false);
        final AtomicBoolean clientClosed = new AtomicBoolean(false);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final EndpointImpl endpoint = new EndpointImpl(executorService, "test-endpoint");
            final Object requestObj = new Object();
            final Object replyObj = new Object();
            try {
            } finally {
                IoUtils.safeClose(endpoint);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testLocalClientSend() throws Throwable {
        final AtomicBoolean clientEndpointClosed = new AtomicBoolean(false);
        final AtomicBoolean clientClosed = new AtomicBoolean(false);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final EndpointImpl endpoint = new EndpointImpl(executorService, "test-endpoint");
            try {
            } finally {
                IoUtils.safeClose(endpoint);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testUnsentReply() throws Throwable {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final EndpointImpl endpoint = new EndpointImpl(executorService, "test-endpoint");
            try {
            } finally {
                IoUtils.safeClose(endpoint);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testUnsentReply2() throws Throwable {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final EndpointImpl endpoint = new EndpointImpl(executorService, "test-endpoint");
            try {
            } finally {
                IoUtils.safeClose(endpoint);
            }
        } finally {
            executorService.shutdownNow();
        }
    }
}
