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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import junit.framework.TestCase;
import org.jboss.remoting3.Client;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.RemoteExecutionException;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.RequestContext;
import org.jboss.remoting3.RequestListener;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class EndpointTestCase extends TestCase {

    private static final Logger log = Logger.getLogger(EndpointTestCase.class);

    private static void enter() {
        log.info("Entering: %s", new Throwable().getStackTrace()[1].getMethodName());
    }

    private static void exit() {
        log.info("Exiting: %s", new Throwable().getStackTrace()[1].getMethodName());
    }

    public void testCreate() throws Throwable {
        enter();
        try {
            final ExecutorService executorService = Executors.newCachedThreadPool();
            final Endpoint endpoint = Remoting.createEndpoint("foo", executorService, OptionMap.EMPTY);
            try {
                endpoint.close();
                executorService.shutdown();
                assertTrue(executorService.awaitTermination(1L, TimeUnit.SECONDS));
            } finally {
                executorService.shutdownNow();
            }
        } finally {
            exit();
        }
    }

    public void testLocalClientInvoke() throws Throwable {
        enter();
        try {
            final Endpoint endpoint = Remoting.getConfiguredEndpoint();
            try {
                final Object requestObj = new Object();
                final Object replyObj = new Object();
                final Client<Object, Object> localClient = Remoting.createLocalClient(endpoint, new RequestListener<Object, Object>() {
                    public void handleRequest(final RequestContext<Object> objectRequestContext, final Object request) throws RemoteExecutionException {
                        try {
                            objectRequestContext.sendReply(replyObj);
                        } catch (IOException e) {
                            throw new RemoteExecutionException(e);
                        }
                    }

                    public void handleClose() {
                        log.info("Listener closed");
                    }
                }, Object.class, Object.class);
                try {
                    assertEquals(replyObj, localClient.invoke(requestObj));
                } finally {
                    IoUtils.safeClose(localClient);
                }
            } finally {
                IoUtils.safeClose(endpoint);
            }
        } finally {
            exit();
        }
    }

    public void testLocalClientSend() throws Throwable {
        enter();
        try {
            final Endpoint endpoint = Remoting.getConfiguredEndpoint();
            try {
                final Object requestObj = new Object();
                final Object replyObj = new Object();
                final Client<Object, Object> localClient = Remoting.createLocalClient(endpoint, new RequestListener<Object, Object>() {
                    public void handleRequest(final RequestContext<Object> objectRequestContext, final Object request) throws RemoteExecutionException {
                        try {
                            objectRequestContext.sendReply(replyObj);
                        } catch (IOException e) {
                            throw new RemoteExecutionException(e);
                        }
                    }

                    public void handleClose() {
                        log.info("Listener closed");
                    }
                }, Object.class, Object.class);
                try {
                    assertEquals(replyObj, localClient.send(requestObj).get());
                } finally {
                    IoUtils.safeClose(localClient);
                }
            } finally {
                IoUtils.safeClose(endpoint);
            }
        } finally {
            exit();
        }
    }
}
