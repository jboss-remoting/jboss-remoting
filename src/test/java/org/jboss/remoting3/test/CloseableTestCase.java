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

package org.jboss.remoting3.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.junit.Test;
import org.xnio.IoUtils;

/**
 *
 */
public final class CloseableTestCase {

    private static final Logger log = Logger.getLogger("test");

    @Test
    public void testBasic() throws Throwable {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final AtomicBoolean closed = new AtomicBoolean();
            final CountDownLatch latch = new CountDownLatch(1);
            final AbstractHandleableCloseable<?> closeable = new AbstractHandleableCloseable(executorService) {
                // empty
            };
            try {
                closeable.addCloseHandler(new CloseHandler<Object>() {
                    public void handleClose(final Object x, final IOException exception) {
                        closed.set(true);
                        latch.countDown();
                    }
                });
                assertFalse(closed.get());
                closeable.close();
                assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                assertTrue(closed.get());
            } finally {
                IoUtils.safeClose(closeable);
            }
        } finally {
            executorService.shutdownNow();
        }
    }
}
