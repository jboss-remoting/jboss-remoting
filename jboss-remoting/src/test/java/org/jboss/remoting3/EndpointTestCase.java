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
import java.io.IOException;
import java.net.URI;
import junit.framework.TestCase;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class EndpointTestCase extends TestCase {

    private static final Logger log = Logger.getLogger(EndpointTestCase.class);

    public void testCreate() throws Throwable {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final Endpoint endpoint = Remoting.createEndpoint(executorService, "foo");
        try {
            endpoint.close();
            executorService.shutdown();
            assertTrue(executorService.awaitTermination(1L, TimeUnit.SECONDS));
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testLocalClientInvoke() throws Throwable {
        final Endpoint endpoint = Remoting.createEndpoint("test-endpoint");
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
    }

    public void testLocalClientSend() throws Throwable {
        final Endpoint endpoint = Remoting.createEndpoint("test-endpoint");
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
    }

    public void testLocalClientConnectInvoke() throws Throwable {
        final Endpoint endpoint = Remoting.createEndpoint("test-endpoint");
        try {
            final Object requestObj = new Object();
            final Object replyObj = new Object();
            final Registration registration = endpoint.serviceBuilder().setGroupName("foo").setServiceType("test").setRequestType(Object.class).
                    setReplyType(Object.class).setClientListener(new ClientListener<Object, Object>() {
                public RequestListener<Object, Object> handleClientOpen(final ClientContext clientContext) {
                    return new RequestListener<Object, Object>() {
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
                    };
                }
            }).register();
            try {
                final Connection connection = endpoint.connect(new URI("local:///"), OptionMap.EMPTY).get();
                try {
                    final Client<Object, Object> localClient = connection.openClient("test", "*", Object.class, Object.class).get();
                    try {
                        assertEquals(replyObj, localClient.invoke(requestObj));
                    } finally {
                        IoUtils.safeClose(localClient);
                    }
                } finally {
                    IoUtils.safeClose(connection);
                }
            } finally {
                IoUtils.safeClose(registration);
            }
        } finally {
            IoUtils.safeClose(endpoint);
        }
    }

    public void testLocalClientConnectSend() throws Throwable {
        final Endpoint endpoint = Remoting.createEndpoint("test-endpoint");
        try {
            final Object requestObj = new Object();
            final Object replyObj = new Object();
            final Registration registration = endpoint.serviceBuilder().setGroupName("foo").setServiceType("test").setRequestType(Object.class).
                    setReplyType(Object.class).setClientListener(new ClientListener<Object, Object>() {
                public RequestListener<Object, Object> handleClientOpen(final ClientContext clientContext) {
                    return new RequestListener<Object, Object>() {
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
                    };
                }
            }).register();
            try {
                final Connection connection = endpoint.connect(new URI("local:///"), OptionMap.EMPTY).get();
                try {
                    final Client<Object, Object> localClient = connection.openClient("test", "*", Object.class, Object.class).get();
                    try {
                        assertEquals(replyObj, localClient.send(requestObj).get());
                    } finally {
                        IoUtils.safeClose(localClient);
                    }
                } finally {
                    IoUtils.safeClose(connection);
                }
            } finally {
                IoUtils.safeClose(registration);
            }
        } finally {
            IoUtils.safeClose(endpoint);
        }
    }

    public void testNotFoundService() throws Throwable {
        final Endpoint endpoint = Remoting.createEndpoint("test-endpoint");
        try {
            endpoint.connect(new URI("local:///"), OptionMap.EMPTY).get().openClient("blah", "bzzt", Object.class, Object.class).get();
        } catch (ServiceNotFoundException e) {
            return;
        }
        fail("Expected exception");
    }
}
