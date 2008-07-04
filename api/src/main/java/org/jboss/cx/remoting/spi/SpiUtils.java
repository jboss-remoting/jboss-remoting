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

package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.spi.remote.ReplyHandler;
import org.jboss.cx.remoting.RequestCancelHandler;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.RequestCompletionHandler;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.xnio.log.Logger;

/**
 * Utility methods for Remoting service providers.
 */
public final class SpiUtils {
    private SpiUtils() {}

    private static final Logger log = Logger.getLogger(SpiUtils.class);

    /**
     * Safely notify a reply handler of an exception.
     *
     * @param replyHandler the reply handler
     * @param msg the message
     * @param cause the cause
     */
    public static void safeHandleException(final ReplyHandler<?> replyHandler, final String msg, final Throwable cause) {
        try {
            replyHandler.handleException(msg, cause);
        } catch (Throwable t) {
            log.error(t, "Failed to properly handle exception");
        }
    }

    /**
     * Safely notify a reply handler of a reply.
     *
     * @param <O> the reply type
     * @param replyHandler the reply handler
     * @param reply the reply
     */
    public static <O> void safeHandleReply(final ReplyHandler<O> replyHandler, final O reply) {
        try {
            replyHandler.handleReply(reply);
        } catch (Throwable t) {
            log.error(t, "Failed to properly handle reply");
        }
    }

    /**
     * Safely notify a reply handler of a cancellation.
     *
     * @param replyHandler the reply handler
     */
    public static void safeHandleCancellation(final ReplyHandler<?> replyHandler) {
        try {
            replyHandler.handleCancellation();
        } catch (Throwable t) {
            log.error(t, "Failed to properly handle cancellation");
        }
    }

    /**
     * Safely notify a request listener's cancel handler of cancellation.
     *
     * @param <O> the reply
     * @param handler the request cancel handler
     * @param requestContext the request context
     * @param mayInterrupt {@code true} if the request listener threads may be interrupted
     */
    public static <O> void safeNotifyCancellation(final RequestCancelHandler<O> handler, final RequestContext<O> requestContext, boolean mayInterrupt) {
        try {
            handler.notifyCancel(requestContext, mayInterrupt);
        } catch (Throwable t) {
            log.error(t, "Request cancel handler threw an exception when calling notifyCancel()");
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
            log.error(t, "Close handler failed unexpectedly");
        }
    }

    /**
     * Safely handle a future request completion.
     *
     * @param <O> the reply type
     * @param handler
     * @param futureReply
     */
    public static <O> void safeHandleRequestCompletion(final RequestCompletionHandler<O> handler, final FutureReply<O> futureReply) {
        try {
            handler.notifyComplete(futureReply);
        } catch (Throwable t) {
            log.error(t, "Request completion handler failed unexpectedly");
        }
    }
}

