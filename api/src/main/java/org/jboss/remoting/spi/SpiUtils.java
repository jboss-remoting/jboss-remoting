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

import org.jboss.remoting.spi.remote.ReplyHandler;
import org.jboss.remoting.spi.remote.RemoteRequestContext;
import org.jboss.remoting.RequestCancelHandler;
import org.jboss.remoting.RequestContext;
import org.jboss.remoting.CloseHandler;
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
    public static void safeHandleException(final ReplyHandler replyHandler, final String msg, final Throwable cause) {
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
    public static <O> void safeHandleReply(final ReplyHandler replyHandler, final O reply) {
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
    public static void safeHandleCancellation(final ReplyHandler replyHandler) {
        try {
            replyHandler.handleCancellation();
        } catch (Throwable t) {
            log.error(t, "Failed to properly handle cancellation");
        }
    }

    /**
     * Safely notify a request listener's cancel handler of cancellation.
     *
     * @param handler the request cancel handler
     * @param requestContext the request context
     */
    public static <O> void safeNotifyCancellation(final RequestCancelHandler<O> handler, final RequestContext<O> requestContext) {
        try {
            handler.notifyCancel(requestContext);
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

    public static RemoteRequestContext getBlankRemoteRequestContext() {
        return BLANK_REMOTE_REQUEST_CONTEXT;
    }

    private static final RemoteRequestContext BLANK_REMOTE_REQUEST_CONTEXT = new BlankRemoteRequestContext();

    private static final class BlankRemoteRequestContext implements RemoteRequestContext {
        public void cancel() {
        }
    }
}

