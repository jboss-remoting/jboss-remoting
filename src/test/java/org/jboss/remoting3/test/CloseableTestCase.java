/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3.test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.xnio.IoUtils;

/**
 *
 */
public final class CloseableTestCase {

    private static final Logger log = Logger.getLogger("test");

    @Rule
    public TestName name = new TestName();

    @Before
    public void doBefore() {
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Running test %s", name.getMethodName());
    }

    @After
    public void doAfter() {
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Finished test %s", name.getMethodName());
    }

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
