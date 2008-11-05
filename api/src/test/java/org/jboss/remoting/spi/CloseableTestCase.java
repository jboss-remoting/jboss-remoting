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

package org.jboss.remoting.spi;

import junit.framework.TestCase;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.xnio.IoUtils;
import org.jboss.remoting.CloseHandler;
import org.jboss.remoting.test.support.LoggingHelper;

/**
 *
 */
public final class CloseableTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testBasic() throws Throwable {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final AtomicBoolean closed = new AtomicBoolean();
            final CountDownLatch latch = new CountDownLatch(1);
            final AbstractHandleableCloseable<Object> closeable = new AbstractHandleableCloseable<Object>(executorService) {
                // empty
            };
            try {
                closeable.addCloseHandler(new CloseHandler<Object>() {
                    public void handleClose(final Object x) {
                        closed.set(true);
                        latch.countDown();
                    }
                });
                assertTrue(closeable.isOpen());
                assertFalse(closed.get());
                closeable.close();
                assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                assertFalse(closeable.isOpen());
                assertTrue(closed.get());
            } finally {
                IoUtils.safeClose(closeable);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testAutoClose() throws Throwable {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final AtomicBoolean closed = new AtomicBoolean();
            final CountDownLatch latch = new CountDownLatch(1);
            final AbstractAutoCloseable<Object> closeable = new AbstractAutoCloseable<Object>(executorService) {
                // empty
            };
            final Handle<Object> rootHandle = closeable.getHandle();
            try {
                closeable.addCloseHandler(new CloseHandler<Object>() {
                    public void handleClose(final Object x) {
                        closed.set(true);
                        latch.countDown();
                    }
                });
                assertTrue(closeable.isOpen());
                assertFalse(closed.get());
                rootHandle.close();
                assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                assertFalse(closeable.isOpen());
                assertTrue(closed.get());
            } finally {
                IoUtils.safeClose(closeable);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testAutoCloseWithOneRef() throws Throwable {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final AtomicBoolean closed = new AtomicBoolean();
            final CountDownLatch latch = new CountDownLatch(1);
            final AbstractAutoCloseable<Object> closeable = new AbstractAutoCloseable<Object>(executorService) {
                // empty
            };
            final Handle<Object> rootHandle = closeable.getHandle();
            try {
                closeable.addCloseHandler(new CloseHandler<Object>() {
                    public void handleClose(final Object x) {
                        closed.set(true);
                        latch.countDown();
                    }
                });
                assertTrue(closeable.isOpen());
                assertFalse(closed.get());
                final Handle<Object> h1 = closeable.getHandle();
                assertTrue(closeable.isOpen());
                assertFalse(closed.get());
                rootHandle.close();
                assertTrue(closeable.isOpen());
                assertFalse(closed.get());
                h1.close();
                assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                assertFalse(closeable.isOpen());
                assertTrue(closed.get());
            } finally {
                IoUtils.safeClose(closeable);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testAutoCloseWithThreeRefs() throws Throwable {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final AtomicBoolean closed = new AtomicBoolean();
            final CountDownLatch latch = new CountDownLatch(1);
            final AbstractAutoCloseable<Object> closeable = new AbstractAutoCloseable<Object>(executorService) {
                // empty
            };
            final Handle<Object> rootHandle = closeable.getHandle();
            try {
                closeable.addCloseHandler(new CloseHandler<Object>() {
                    public void handleClose(final Object x) {
                        closed.set(true);
                        latch.countDown();
                    }
                });
                assertTrue(closeable.isOpen());
                assertFalse(closed.get());
                final Handle<Object> h1 = closeable.getHandle();
                final Handle<Object> h2 = closeable.getHandle();
                final Handle<Object> h3 = closeable.getHandle();
                assertTrue(closeable.isOpen());
                assertFalse(closed.get());
                rootHandle.close();
                assertTrue(closeable.isOpen());
                assertFalse(closed.get());
                h1.close();
                assertTrue(closeable.isOpen());
                assertFalse(closed.get());
                h2.close();
                assertTrue(closeable.isOpen());
                assertFalse(closed.get());
                h3.close();
                assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
                assertFalse(closeable.isOpen());
                assertTrue(closed.get());
            } finally {
                IoUtils.safeClose(closeable);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testHandlerRemoval() throws Throwable {
        final Executor executor = IoUtils.directExecutor();
        final AbstractAutoCloseable<Object> closeable = new AbstractAutoCloseable<Object>(executor) {
            // empty
        };
        final Handle<Object> rootHandle = closeable.getHandle();
        try {
            // todo - something with that rootHandle
        } finally {
            IoUtils.safeClose(closeable);
        }
    }
}
