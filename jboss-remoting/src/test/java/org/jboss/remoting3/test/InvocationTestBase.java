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
import junit.framework.TestCase;
import org.jboss.remoting3.Client;
import org.jboss.remoting3.ClientConnector;
import org.jboss.remoting3.ClientContext;
import org.jboss.remoting3.ClientListener;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.InvocationTestObject;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.RemoteExecutionException;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.RequestContext;
import org.jboss.remoting3.RequestListener;
import org.jboss.remoting3.ServiceNotFoundException;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.log.Logger;

public abstract class InvocationTestBase extends TestCase {
    private static final Logger log = Logger.getLogger(InvocationTestBase.class);

    protected Endpoint endpoint;

    public void setUp() throws IOException {
        enter();
        try {
            endpoint = Remoting.getConfiguredEndpoint();
        } finally {
            exit();
        }
    }

    private static void enter() {
        log.info("Entering: %s", new Throwable().getStackTrace()[1].getMethodName());
    }

    private static void exit() {
        log.info("Exiting: %s", new Throwable().getStackTrace()[1].getMethodName());
    }

    protected abstract Connection getConnection() throws IOException;

    public void testBasicInvoke() throws IOException {
        enter();
        try {
            final InvocationTestObject requestObj = new InvocationTestObject();
            final InvocationTestObject replyObj = new InvocationTestObject();
            final Registration registration = endpoint.serviceBuilder().setGroupName("foo").setServiceType("test1").setRequestType(InvocationTestObject.class).
                    setReplyType(InvocationTestObject.class).setClientListener(new ClientListener<InvocationTestObject, InvocationTestObject>() {
                public RequestListener<InvocationTestObject, InvocationTestObject> handleClientOpen(final ClientContext clientContext) {
                    return new RequestListener<InvocationTestObject, InvocationTestObject>() {
                        public void handleRequest(final RequestContext<InvocationTestObject> objectRequestContext, final InvocationTestObject request) throws RemoteExecutionException {
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
                final Connection connection = getConnection();
                try {
                    final Client<InvocationTestObject, InvocationTestObject> client = connection.openClient("test1", "*", InvocationTestObject.class, InvocationTestObject.class).get();
                    try {
                        assertEquals(replyObj, client.invoke(requestObj));
                    } finally {
                        IoUtils.safeClose(client);
                    }
                } finally {
                    IoUtils.safeClose(connection);
                }
            } finally {
                IoUtils.safeClose(registration);
            }
        } finally {
            exit();
        }
    }

    public void testBasicSend() throws IOException {
        enter();
        try {
            final InvocationTestObject requestObj = new InvocationTestObject();
            final InvocationTestObject replyObj = new InvocationTestObject();
            final Registration registration = endpoint.serviceBuilder().setGroupName("foo").setServiceType("test2").setRequestType(InvocationTestObject.class).
                    setReplyType(InvocationTestObject.class).setClientListener(new ClientListener<InvocationTestObject, InvocationTestObject>() {
                public RequestListener<InvocationTestObject, InvocationTestObject> handleClientOpen(final ClientContext clientContext) {
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

                        public void handleClose() {
                            log.info("Listener closed");
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
                    }
                } finally {
                    IoUtils.safeClose(connection);
                }
            } finally {
                IoUtils.safeClose(registration);
            }
        } finally {
            exit();
        }
    }

    public void testBasicClientConnector() throws Throwable {
        enter();
        try {
            final InvocationTestObject requestObj = new InvocationTestObject();
            final InvocationTestObject replyObj = new InvocationTestObject();

            final Registration registration = endpoint.serviceBuilder().setGroupName("foo").setServiceType("test3").setRequestType(ClientConnector.class).
                    setReplyType(InvocationTestObject.class).setClientListener(new ClientListener<ClientConnector, InvocationTestObject>() {
                public RequestListener<ClientConnector, InvocationTestObject> handleClientOpen(final ClientContext clientContext) {
                    return new RequestListener<ClientConnector, InvocationTestObject>() {
                        public void handleRequest(final RequestContext<InvocationTestObject> objectRequestContext, final ClientConnector request) throws RemoteExecutionException {
                            try {
                                assertEquals(replyObj, ((ClientConnector<InvocationTestObject, InvocationTestObject>)request).getFutureClient().get().invoke(requestObj));
                                objectRequestContext.sendReply(replyObj);
                            } catch (Throwable e) {
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
                final Connection connection = getConnection();
                try {
                    final Client<ClientConnector, InvocationTestObject> client = connection.openClient("test3", "*", ClientConnector.class, InvocationTestObject.class).get();
                    try {
                        client.invoke(connection.createClientConnector(new RequestListener<InvocationTestObject, InvocationTestObject>() {
                            public void handleRequest(final RequestContext<InvocationTestObject> requestContext, final InvocationTestObject request) throws RemoteExecutionException {
                                try {
                                    requestContext.sendReply(replyObj);
                                } catch (IOException e) {
                                    throw new RemoteExecutionException(e);
                                }
                            }

                            public void handleClose() {
                                log.info("Inner listener closed");
                            }
                        }, InvocationTestObject.class, InvocationTestObject.class));
                    } finally {
                        IoUtils.safeClose(client);
                    }
                } finally {
                    IoUtils.safeClose(connection);
                }
            } finally {
                IoUtils.safeClose(registration);
            }
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

    public void tearDown() throws IOException {
        enter();
        try {
            Xnio.getInstance().close();
        } finally {
            exit();
        }
    }
}
