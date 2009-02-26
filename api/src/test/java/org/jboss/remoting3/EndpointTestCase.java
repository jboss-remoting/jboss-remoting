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

import junit.framework.TestCase;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import org.jboss.remoting3.AbstractRequestListener;
import org.jboss.remoting3.RequestContext;
import org.jboss.remoting3.RemoteExecutionException;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Client;
import org.jboss.remoting3.IndeterminateOutcomeException;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.Handle;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.IoFuture;
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
                final Handle<RequestHandler> handle = endpoint.createRequestHandler(new AbstractRequestListener<Object, Object>() {
                    public void handleRequest(final RequestContext<Object> context, final Object request) throws RemoteExecutionException {
                        assertEquals(request, requestObj);
                        try {
                            context.sendReply(replyObj);
                        } catch (IOException e) {
                            log.error(e, "Error sending reply!");
                        }
                    }
                }, Object.class, Object.class);
                try {
                    final RequestHandler requestHandler = handle.getResource();
                    try {
                        requestHandler.addCloseHandler(new CloseHandler<RequestHandler>() {
                            public void handleClose(final RequestHandler closed) {
                                clientEndpointClosed.set(true);
                            }
                        });
                        final Client<Object,Object> client = endpoint.createClient(requestHandler, Object.class, Object.class);
                        try {
                            client.addCloseHandler(new CloseHandler<Client<Object, Object>>() {
                                public void handleClose(final Client<Object, Object> closed) {
                                    clientClosed.set(true);
                                }
                            });
                            handle.close();
                            assertEquals(replyObj, client.invoke(requestObj));
                            client.close();
                            executorService.shutdown();
                            assertTrue(executorService.awaitTermination(1L, TimeUnit.SECONDS));
                            assertTrue(clientEndpointClosed.get());
                            assertTrue(clientClosed.get());
                        } finally {
                            IoUtils.safeClose(client);
                        }
                    } finally {
                        IoUtils.safeClose(requestHandler);
                    }
                } finally {
                    IoUtils.safeClose(handle);
                }
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
                final Object requestObj = new Object();
                final Object replyObj = new Object();
                final Handle<RequestHandler> handle = endpoint.createRequestHandler(new AbstractRequestListener<Object, Object>() {
                    public void handleRequest(final RequestContext<Object> context, final Object request) throws RemoteExecutionException {
                        assertEquals(request, requestObj);
                        try {
                            context.sendReply(replyObj);
                        } catch (IOException e) {
                            log.error(e, "Error sending reply!");
                        }
                    }
                }, Object.class, Object.class);
                try {
                    final RequestHandler requestHandler = handle.getResource();
                    try {
                        requestHandler.addCloseHandler(new CloseHandler<RequestHandler>() {
                            public void handleClose(final RequestHandler closed) {
                                clientEndpointClosed.set(true);
                            }
                        });
                        final Client<Object,Object> client = endpoint.createClient(requestHandler, Object.class, Object.class);
                        try {
                            client.addCloseHandler(new CloseHandler<Client<Object, Object>>() {
                                public void handleClose(final Client<Object, Object> closed) {
                                    clientClosed.set(true);
                                }
                            });
                            handle.close();
                            final IoFuture<? extends Object> futureReply = client.send(requestObj);
                            assertEquals(IoFuture.Status.DONE, futureReply.await(1L, TimeUnit.SECONDS));
                            assertEquals(replyObj, futureReply.get());
                            client.close();
                            executorService.shutdown();
                            assertTrue(executorService.awaitTermination(1L, TimeUnit.SECONDS));
                            assertTrue(clientEndpointClosed.get());
                            assertTrue(clientClosed.get());
                        } finally {
                            IoUtils.safeClose(client);
                        }
                    } finally {
                        IoUtils.safeClose(requestHandler);
                    }
                } finally {
                    IoUtils.safeClose(handle);
                }
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
                final Object requestObj = new Object();
                final Handle<RequestHandler> handle = endpoint.createRequestHandler(new AbstractRequestListener<Object, Object>() {
                    public void handleRequest(final RequestContext<Object> context, final Object request) throws RemoteExecutionException {
                        assertEquals(request, requestObj);
                        // don't send a reply!!
                    }
                }, Object.class, Object.class);
                try {
                    final RequestHandler requestHandler = handle.getResource();
                    try {
                        final Client<Object,Object> client = endpoint.createClient(requestHandler, Object.class, Object.class);
                        try {
                            final IoFuture<? extends Object> futureReply = client.send(requestObj);
                            assertEquals(IoFuture.Status.FAILED, futureReply.await(500L, TimeUnit.MILLISECONDS));
                            assertTrue(futureReply.getException() instanceof IndeterminateOutcomeException);
                        } finally {
                            IoUtils.safeClose(client);
                        }
                    } finally {
                        IoUtils.safeClose(requestHandler);
                    }
                } finally {
                    IoUtils.safeClose(handle);
                }
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
                final Object requestObj = new Object();
                final Handle<RequestHandler> handle = endpoint.createRequestHandler(new AbstractRequestListener<Object, Object>() {
                    public void handleRequest(final RequestContext<Object> context, final Object request) throws RemoteExecutionException {
                        assertEquals(request, requestObj);
                        context.execute(new Runnable() {
                            public void run() {
                                context.execute(new Runnable() {
                                    public void run() {
                                        context.execute(new Runnable() {
                                            public void run() {
                                            }
                                        });
                                    }
                                });
                                context.execute(new Runnable() {
                                    public void run() {
                                    }
                                });
                            }
                        });
                        context.execute(new Runnable() {
                            public void run() {
                            }
                        });
                        // don't send a reply!!
                    }
                }, Object.class, Object.class);
                try {
                    final RequestHandler requestHandler = handle.getResource();
                    try {
                        final Client<Object,Object> client = endpoint.createClient(requestHandler, Object.class, Object.class);
                        try {
                            final IoFuture<? extends Object> futureReply = client.send(requestObj);
                            assertEquals(IoFuture.Status.FAILED, futureReply.await(500L, TimeUnit.MILLISECONDS));
                            assertTrue(futureReply.getException() instanceof IndeterminateOutcomeException);
                        } finally {
                            IoUtils.safeClose(client);
                        }
                    } finally {
                        IoUtils.safeClose(requestHandler);
                    }
                } finally {
                    IoUtils.safeClose(handle);
                }
            } finally {
                IoUtils.safeClose(endpoint);
            }
        } finally {
            executorService.shutdownNow();
        }
    }
}
