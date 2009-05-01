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

package org.jboss.remoting3.spi;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.RequestCancelHandler;
import org.jboss.remoting3.RequestContext;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.log.Logger;

/**
 * Utility methods for Remoting service providers.
 */
public final class SpiUtils {

    private SpiUtils() {}

    private static final Logger heLog = Logger.getLogger("org.jboss.remoting.handler-errors");

    /**
     * Safely notify a reply handler of an exception.
     *
     * @param replyHandler the reply handler
     * @param exception
     */
    public static void safeHandleException(final ReplyHandler replyHandler, final IOException exception) {
        try {
            replyHandler.handleException(exception);
        } catch (Throwable t) {
            heLog.debug(t, "Failed to properly handle exception");
        }
    }

    /**
     * Safely notify a reply handler of a reply.
     *
     * @param <O> the reply type
     * @param replyHandler the reply handler
     * @param reply the reply
     */
    public static <O> void safeHandleReply(final ReplyHandler replyHandler, final O reply) {
        try {
            replyHandler.handleReply(reply);
        } catch (Throwable t) {
            heLog.debug(t, "Failed to properly handle reply");
        }
    }

    /**
     * Safely notify a reply handler of a cancellation.
     *
     * @param replyHandler the reply handler
     */
    public static void safeHandleCancellation(final ReplyHandler replyHandler) {
        try {
            replyHandler.handleCancellation();
        } catch (Throwable t) {
            heLog.debug(t, "Failed to properly handle cancellation");
        }
    }

    /**
     * Safely notify a request listener's cancel handler of cancellation.
     *
     * @param <O> the reply type
     * @param handler the request cancel handler
     * @param requestContext the request context
     */
    public static <O> void safeNotifyCancellation(final RequestCancelHandler<O> handler, final RequestContext<O> requestContext) {
        try {
            handler.notifyCancel(requestContext);
        } catch (Throwable t) {
            heLog.error(t, "Request cancel handler threw an exception");
        }
    }

    /**
     * Safely handle a close notification.
     *
     * @param <T> the type of the closable resource
     * @param handler the close handler
     * @param closed the object that was closed
     */
    public static <T> void safeHandleClose(final CloseHandler<? super T> handler, final T closed) {
        try {
            handler.handleClose(closed);
        } catch (Throwable t) {
            heLog.error(t, "Close handler threw an exception");
        }
    }

    /**
     * Get a {@code Cancellable} for an {@code IoFuture}.
     *
     * @param future the future
     * @return the cancellable
     */
    public static Cancellable cancellable(final IoFuture<?> future) {
        return new Cancellable() {
            public void cancel() {
                future.cancel();
            }
        };
    }

    /**
     * Create a connection handler factory for a public class which implements {@code ConnectionHandler} and has a
     * public constructor which accepts a {@code ConnectionHandler} as its sole parameter.
     *
     * @param handlerClass the class of the handler
     * @param <T> the type of the handler
     * @return the handler factory
     * @throws IllegalArgumentException if the class does not meet the requirements
     */
    public static <T extends ConnectionHandler> ConnectionHandlerFactory connectionHandlerFactory(final Class<T> handlerClass) throws IllegalArgumentException {
        return AccessController.doPrivileged(new PrivilegedAction<ConnectionHandlerFactory>() {
            public ConnectionHandlerFactory run() {
                final Constructor<T> constructor;
                try {
                    constructor = handlerClass.getConstructor(ConnectionHandler.class);
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException("No valid constructor is present");
                }
                if ((handlerClass.getModifiers() & constructor.getModifiers() & Modifier.PUBLIC) == 0) {
                    throw new IllegalArgumentException("Class or constructor is not public");
                }
                for (Class<?> exceptionType : constructor.getExceptionTypes()) {
                    if (Exception.class.isAssignableFrom(exceptionType) && ! RuntimeException.class.isAssignableFrom(exceptionType)) {
                        throw new IllegalArgumentException("Constructor may not throw checked exceptions");
                    }
                }
                return new ConnectionHandlerFactory() {
                    public ConnectionHandler createInstance(final ConnectionHandler localConnectionHandler) {
                        return AccessController.doPrivileged(new PrivilegedAction<ConnectionHandler>() {
                            public ConnectionHandler run() {
                                try {
                                    return constructor.newInstance(localConnectionHandler);
                                } catch (InstantiationException e) {
                                    throw new IllegalStateException("Unexpected exception", e);
                                } catch (IllegalAccessException e) {
                                    throw new IllegalStateException("Unexpected exception", e);
                                } catch (InvocationTargetException e) {
                                    throw new IllegalStateException("Unexpected exception", e.getCause());
                                }
                            }
                        });
                    }
                };
            }
        });
    }

    /**
     * Create a connection provider factory for a public class which implements {@code ConnectionProvider} and has a
     * public constructor which accepts a {@code ConnectionProviderContext} as its sole parameter.
     *
     * @param providerClass the class of the provider
     * @param <T> the type of the provider
     * @return the provider factory
     * @throws IllegalArgumentException if the class does not meet the requirements
     */
    public static <T extends ConnectionProvider> ConnectionProviderFactory connectionProviderFactory(final Class<T> providerClass) throws IllegalArgumentException {
        return AccessController.doPrivileged(new PrivilegedAction<ConnectionProviderFactory>() {
            public ConnectionProviderFactory run() {
                final Constructor<T> constructor;
                try {
                    constructor = providerClass.getConstructor(ConnectionProviderContext.class);
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException("No valid constructor is present");
                }
                if ((providerClass.getModifiers() & constructor.getModifiers() & Modifier.PUBLIC) == 0) {
                    throw new IllegalArgumentException("Class or constructor is not public");
                }
                for (Class<?> exceptionType : constructor.getExceptionTypes()) {
                    if (Exception.class.isAssignableFrom(exceptionType) && ! RuntimeException.class.isAssignableFrom(exceptionType)) {
                        throw new IllegalArgumentException("Constructor may not throw checked exceptions");
                    }
                }
                return new ConnectionProviderFactory() {
                    public ConnectionProvider createInstance(final ConnectionProviderContext context) {
                        return AccessController.doPrivileged(new PrivilegedAction<ConnectionProvider>() {
                            public ConnectionProvider run() {
                                try {
                                    return constructor.newInstance(context);
                                } catch (InstantiationException e) {
                                    throw new IllegalStateException("Unexpected exception", e);
                                } catch (IllegalAccessException e) {
                                    throw new IllegalStateException("Unexpected exception", e);
                                } catch (InvocationTargetException e) {
                                    throw new IllegalStateException("Unexpected exception", e.getCause());
                                }
                            }
                        });
                    }
                };
            }
        });
    }

    /**
     * Get a remote request context that simply ignores a cancel request.
     *
     * @return a blank remote request context
     */
    public static RemoteRequestContext getBlankRemoteRequestContext() {
        return BLANK_REMOTE_REQUEST_CONTEXT;
    }

    private static final RemoteRequestContext BLANK_REMOTE_REQUEST_CONTEXT = new BlankRemoteRequestContext();

    private static final class BlankRemoteRequestContext implements RemoteRequestContext {
        public void cancel() {
        }
    }
}

