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

import java.io.IOException;
import org.jboss.marshalling.river.RiverMarshaller;
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
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.log.Logger;
import org.testng.SkipException;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test
public abstract class InvocationTestBase {
    private static final Logger log = Logger.getLogger(InvocationTestBase.class);

    protected Endpoint endpoint;

    @BeforeTest
    public void setUp() throws IOException {
        enter();
        try {
            Thread.currentThread().setContextClassLoader(RiverMarshaller.class.getClassLoader());
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
                public RequestListener<InvocationTestObject, InvocationTestObject> handleClientOpen(final ClientContext clientContext) {
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
                    final Client<InvocationTestObject, InvocationTestObject> client = connection.openClient("test1", "*", InvocationTestObject.class, InvocationTestObject.class).get();
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
                public RequestListener<InvocationTestObject, InvocationTestObject> handleClientOpen(final ClientContext clientContext) {
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
                public RequestListener<ClientConnector, InvocationTestObject> handleClientOpen(final ClientContext clientContext) {
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
