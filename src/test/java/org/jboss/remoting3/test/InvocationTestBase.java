/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.remoting3.Client;
import org.jboss.remoting3.ClientConnector;
import org.jboss.remoting3.ClientContext;
import org.jboss.remoting3.ClientListener;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.RemoteExecutionException;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.RequestContext;
import org.jboss.remoting3.RequestListener;
import org.jboss.remoting3.ServiceNotFoundException;
import org.jboss.remoting3.stream.Streams;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Options;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.log.Logger;
import org.testng.SkipException;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test
public abstract class InvocationTestBase {
    private static final Logger log = Logger.getLogger("test");

    protected Endpoint endpoint;

    @BeforeTest
    public void setUp() throws IOException {
        log.info("::::: STARTING TEST FOR: %s :::::", getClass().getName());
        enter();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            endpoint = Remoting.getConfiguredEndpoint();
        } finally {
            exit();
        }
    }

    static void enter() {
        final StackTraceElement e = new Throwable().getStackTrace()[1];
        log.info("Entering: %s#%s", e.getClassName(), e.getMethodName());
    }

    static void exit() {
        final StackTraceElement e = new Throwable().getStackTrace()[1];
        log.info("Exiting: %s#%s", e.getClassName(), e.getMethodName());
        log.info("-------------------------------------------------------------");
    }

    protected abstract Connection getConnection() throws Exception;

    public void testBasicInvoke() throws Exception {
        enter();
        try {
            final InvocationTestObject requestObj = new InvocationTestObject();
            final InvocationTestObject replyObj = new InvocationTestObject();
            final Registration registration = endpoint.serviceBuilder().setGroupName("foo").setServiceType("test1").setRequestType(InvocationTestObject.class).
                    setReplyType(InvocationTestObject.class).setClientListener(new ClientListener<InvocationTestObject, InvocationTestObject>() {
                public RequestListener<InvocationTestObject, InvocationTestObject> handleClientOpen(final ClientContext clientContext, final OptionMap optionMap) {
                    clientContext.addCloseHandler(new CloseHandler<ClientContext>() {
                        public void handleClose(final ClientContext closed) {
                            log.info("Client closed");
                        }
                    });
                    return new RequestListener<InvocationTestObject, InvocationTestObject>() {
                        public void handleRequest(final RequestContext<InvocationTestObject> objectRequestContext, final InvocationTestObject request) throws RemoteExecutionException {
                            try {
                                log.info("Got request %s, sending reply %s", request, replyObj);
                                objectRequestContext.sendReply(replyObj);
                            } catch (IOException e) {
                                throw new RemoteExecutionException(e);
                            }
                        }
                    };
                }
            }).register();
            try {
                final Connection connection = getConnection();
                try {
                    final Client<InvocationTestObject, InvocationTestObject> client = connection.openClient("test1", "*", InvocationTestObject.class, InvocationTestObject.class, getClass().getClassLoader(), OptionMap.EMPTY).get();
                    try {
                        assertEquals(replyObj, client.invoke(requestObj));
                    } finally {
                        IoUtils.safeClose(client);
                        client.awaitClosedUninterruptibly();
                    }
                } finally {
                    IoUtils.safeClose(connection);
                    connection.awaitClosedUninterruptibly();
                }
            } finally {
                IoUtils.safeClose(registration);
                registration.awaitClosedUninterruptibly();
            }
        } finally {
            exit();
        }
    }

    public void testBasicSend() throws Exception {
        enter();
        try {
            final InvocationTestObject requestObj = new InvocationTestObject();
            final InvocationTestObject replyObj = new InvocationTestObject();
            final Registration registration = endpoint.serviceBuilder().setGroupName("foo").setServiceType("test2").setRequestType(InvocationTestObject.class).
                    setReplyType(InvocationTestObject.class).setClientListener(new ClientListener<InvocationTestObject, InvocationTestObject>() {
                public RequestListener<InvocationTestObject, InvocationTestObject> handleClientOpen(final ClientContext clientContext, final OptionMap optionMap) {
                    clientContext.addCloseHandler(new CloseHandler<ClientContext>() {
                        public void handleClose(final ClientContext closed) {
                            log.info("Listener closed");
                        }
                    });
                    return new RequestListener<InvocationTestObject, InvocationTestObject>() {
                        public void handleRequest(final RequestContext<InvocationTestObject> objectRequestContext, final InvocationTestObject request) throws RemoteExecutionException {
                            try {
                                log.info("Got request %s, sending reply %s", request, replyObj);
                                objectRequestContext.sendReply(replyObj);
                            } catch (IOException e) {
                                log.error(e, "reply");
                                throw new RemoteExecutionException(e);
                            }
                        }
                    };
                }
            }).register();
            try {
                final Connection connection = getConnection();
                try {
                    final Client<InvocationTestObject, InvocationTestObject> client = connection.openClient("test2", "*", InvocationTestObject.class, InvocationTestObject.class).get();
                    try {
                        assertEquals(replyObj, client.send(requestObj).get());
                    } finally {
                        IoUtils.safeClose(client);
                        client.awaitClosedUninterruptibly();
                    }
                } finally {
                    IoUtils.safeClose(connection);
                    connection.awaitClosedUninterruptibly();
                }
            } finally {
                IoUtils.safeClose(registration);
                registration.awaitClosedUninterruptibly();
            }
        } finally {
            exit();
        }
    }

    public void testBasicClientConnector() throws Exception {
        enter();
        try {
            final InvocationTestObject requestObj = new InvocationTestObject();
            final InvocationTestObject replyObj = new InvocationTestObject();

            final Registration registration = endpoint.serviceBuilder().setGroupName("foo").setServiceType("test3").setRequestType(ClientConnector.class).
                    setReplyType(InvocationTestObject.class).setClientListener(new ClientListener<ClientConnector, InvocationTestObject>() {
                public RequestListener<ClientConnector, InvocationTestObject> handleClientOpen(final ClientContext clientContext, final OptionMap optionMap) {
                    clientContext.addCloseHandler(new CloseHandler<ClientContext>() {
                        public void handleClose(final ClientContext closed) {
                            log.info("Listener closed");
                        }
                    });
                    return new RequestListener<ClientConnector, InvocationTestObject>() {
                        public void handleRequest(final RequestContext<InvocationTestObject> objectRequestContext, final ClientConnector request) throws RemoteExecutionException {
                            try {
                                assertEquals(replyObj, ((ClientConnector<InvocationTestObject, InvocationTestObject>)request).getFutureClient().get().invoke(requestObj));
                                log.info("Got request %s, sending reply %s", request, replyObj);
                                objectRequestContext.sendReply(replyObj);
                            } catch (Throwable e) {
                                throw new RemoteExecutionException(e);
                            }
                        }
                    };
                }
            }).register();
            try {
                final Connection connection = getConnection();
                try {
                    final Client<ClientConnector, InvocationTestObject> client = connection.openClient("test3", "*", ClientConnector.class, InvocationTestObject.class).get();
                    try {
                        final ClientConnector<InvocationTestObject, InvocationTestObject> clientConnector = connection.createClientConnector(new RequestListener<InvocationTestObject, InvocationTestObject>() {
                            public void handleRequest(final RequestContext<InvocationTestObject> requestContext, final InvocationTestObject request) throws RemoteExecutionException {
                                try {
                                    log.info("Got request %s, sending reply %s", request, replyObj);
                                    requestContext.sendReply(replyObj);
                                } catch (IOException e) {
                                    throw new RemoteExecutionException(e);
                                }
                            }
                        }, InvocationTestObject.class, InvocationTestObject.class);
                        final ClientContext context = clientConnector.getClientContext();
                        context.addCloseHandler(new CloseHandler<ClientContext>() {
                            public void handleClose(final ClientContext closed) {
                                log.info("Inner listener closed");
                            }
                        });
                        try {
                            client.invoke(clientConnector);
                        } finally {
                            IoUtils.safeClose(context);
                            context.awaitClosedUninterruptibly();
                        }
                    } finally {
                        IoUtils.safeClose(client);
                        client.awaitClosedUninterruptibly();
                    }
                } finally {
                    IoUtils.safeClose(connection);
                    connection.awaitClosedUninterruptibly();
                }
            } finally {
                IoUtils.safeClose(registration);
                registration.awaitClosedUninterruptibly();
            }
        } catch (UnsupportedOperationException e) {
            throw new SkipException("Skipping test due to unsupported createClientConnector");
        } finally {
            exit();
        }
    }

    public void testNotFoundService() throws Throwable {
        enter();
        try {
            final Connection connection = getConnection();
            try {
                connection.openClient("blah", "bzzt", Object.class, Object.class).get();
            } catch (ServiceNotFoundException e) {
                return;
            } finally {
                IoUtils.safeClose(connection);
            }
            fail("Expected exception");
        } finally {
            exit();
        }
    }

    public void testOptions() throws Throwable {
        enter();
        try {
            final OptionMap optionMap = OptionMap.builder().set(Options.BROADCAST, true).getMap();
            final AtomicReference<OptionMap> receivedOptions = new AtomicReference<OptionMap>();
            final Registration registration = endpoint.serviceBuilder().setGroupName("foo").setServiceType("test1").setRequestType(InvocationTestObject.class).
                    setReplyType(InvocationTestObject.class).setClientListener(new ClientListener<InvocationTestObject, InvocationTestObject>() {
                public RequestListener<InvocationTestObject, InvocationTestObject> handleClientOpen(final ClientContext clientContext, final OptionMap optionMap) {
                    receivedOptions.set(optionMap);
                    clientContext.addCloseHandler(new CloseHandler<ClientContext>() {
                        public void handleClose(final ClientContext closed) {
                            log.info("Client closed");
                        }
                    });
                    return new RequestListener<InvocationTestObject, InvocationTestObject>() {
                        public void handleRequest(final RequestContext<InvocationTestObject> objectRequestContext, final InvocationTestObject request) throws RemoteExecutionException {
                            // not invoked
                        }
                    };
                }
            }).register();
            try {
                final Connection connection = getConnection();
                try {
                    final Client<InvocationTestObject, InvocationTestObject> client = connection.openClient("test1", "*", InvocationTestObject.class, InvocationTestObject.class, getClass().getClassLoader(), optionMap).get();
                    try {
                        assertTrue(optionMap.contains(Options.BROADCAST), "Option disappeared from original map");
                        assertTrue(optionMap.get(Options.BROADCAST).booleanValue(), "Option changed value from original map");
                        final OptionMap map2 = receivedOptions.get();
                        assertNotNull(map2, "Option map was not received");
                        assertTrue(map2.contains(Options.BROADCAST), "Option does not appear in destination map");
                        assertTrue(map2.get(Options.BROADCAST).booleanValue(), "Option changed value in destination map");
                    } finally {
                        IoUtils.safeClose(client);
                        client.awaitClosedUninterruptibly();
                    }
                } finally {
                    IoUtils.safeClose(connection);
                    connection.awaitClosedUninterruptibly();
                }
            } finally {
                IoUtils.safeClose(registration);
                registration.awaitClosedUninterruptibly();
            }
        } finally {
            exit();
        }
    }

    public void testInputStream() throws Throwable {
        enter();
        try {
            final Registration registration = endpoint.serviceBuilder(InputStream.class, InputStream.class).setServiceType("streamtest").setClientListener(new ClientListener<InputStream, InputStream>() {
                public RequestListener<InputStream, InputStream> handleClientOpen(final ClientContext clientContext, final OptionMap optionMap) {
                    return new RequestListener<InputStream, InputStream>() {
                        public void handleRequest(final RequestContext<InputStream> context, final InputStream request) throws RemoteExecutionException {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try {
                                Streams.copyStream(request, baos);
                            } catch (IOException e) {
                                try {
                                    context.sendFailure("I/O error", e);
                                } catch (IOException e1) {
                                    // blah
                                }
                            }
                            try {
                                context.sendReply(new ByteArrayInputStream(baos.toByteArray()));
                            } catch (IOException e) {
                                // blah
                            }
                        }
                    };
                }
            }).register();
            try {
                final Connection connection = getConnection();
                try {
                    final Client<InputStream, InputStream> client = connection.openClient("streamtest", "*", InputStream.class, InputStream.class, InvocationTestBase.class.getClassLoader(), OptionMap.EMPTY).get();
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        Streams.copyStream(client.invoke(new ByteArrayInputStream("This is a test!!!".getBytes())), baos);
                        assertEquals(new String(baos.toByteArray()), "This is a test!!!");
                    } finally {
                        IoUtils.safeClose(client);
                        client.awaitClosedUninterruptibly();
                    }
                } finally {
                    IoUtils.safeClose(connection);
                    connection.awaitClosedUninterruptibly();
                }
            } finally {
                IoUtils.safeClose(registration);
                registration.awaitClosedUninterruptibly();
            }
        } finally {
            exit();
        }
    }

    public void testOutputStream() throws Throwable {
        enter();
        try {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            final Registration registration = endpoint.serviceBuilder(OutputStream.class, OutputStream.class).setServiceType("streamtest").setClientListener(new ClientListener<OutputStream, OutputStream>() {
                public RequestListener<OutputStream, OutputStream> handleClientOpen(final ClientContext clientContext, final OptionMap optionMap) {
                    return new RequestListener<OutputStream, OutputStream>() {
                        public void handleRequest(final RequestContext<OutputStream> context, final OutputStream request) throws RemoteExecutionException {
                            try {
                                Streams.copyStream(new ByteArrayInputStream("This is a test...".getBytes()), request);
                            } catch (IOException e) {
                                try {
                                    context.sendFailure("I/O error", e);
                                } catch (IOException e1) {
                                    // blah
                                }
                            }
                            try {
                                context.sendReply(os);
                            } catch (IOException e) {
                                // blah
                            }
                        }
                    };
                }
            }).register();
            try {
                final Connection connection = getConnection();
                try {
                    final Client<OutputStream, OutputStream> client = connection.openClient("streamtest", "*", OutputStream.class, OutputStream.class, InvocationTestBase.class.getClassLoader(), OptionMap.EMPTY).get();
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        final OutputStream result = client.invoke(baos);
                        assertEquals(new String(baos.toByteArray()), "This is a test...");
                        Streams.copyStream(new ByteArrayInputStream("This is a test #2...".getBytes()), result);
                        // this test can't finish in time
//                        assertEquals(new String(os.toByteArray()), "This is a test #2...");
                    } finally {
                        IoUtils.safeClose(client);
                        client.awaitClosedUninterruptibly();
                    }
                } finally {
                    IoUtils.safeClose(connection);
                    connection.awaitClosedUninterruptibly();
                }
            } finally {
                IoUtils.safeClose(registration);
                registration.awaitClosedUninterruptibly();
            }
        } finally {
            exit();
        }
    }

    @AfterTest
    public void tearDown() throws IOException {
        enter();
        try {
            Xnio.getInstance().close();
            System.runFinalization();
        } finally {
            exit();
        }
    }
}
