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

import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.remoting3.spi.RemoteRequestContext;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.xnio.AbstractIoFuture;
import org.jboss.xnio.IoFuture;

/**
 *
 */
final class FutureReplyImpl<O> extends AbstractIoFuture<O> {

    private final Executor executor;
    private final Class<O> replyType;
    private final ReplyHandler replyHandler = new Handler();
    private volatile RemoteRequestContext remoteRequestContext;

    FutureReplyImpl(final Executor executor, final Class<O> replyType) {
        this.executor = executor;
        this.replyType = replyType;
    }

    void setRemoteRequestContext(final RemoteRequestContext remoteRequestContext) {
        this.remoteRequestContext = remoteRequestContext;
    }

    public IoFuture<O> cancel() {
        // must not be called before setRemoteRequestContext
        remoteRequestContext.cancel();
        return this;
    }

    protected Executor getNotifierExecutor() {
        return executor;
    }

    ReplyHandler getReplyHandler() {
        return replyHandler;
    }

    private final class Handler implements ReplyHandler {

        public void handleReply(final Object reply) {
            final Class<O> replyType = FutureReplyImpl.this.replyType;
            final O actualReply;
            try {
                actualReply = replyType.cast(reply);
            } catch (ClassCastException e) {
                // reply can't be null, else we wouldn't be here...
                final Class<? extends Object> actualReplyType = reply.getClass();
                final String actualReplyTypeName = actualReplyType.getName();
                final String replyTypeName = replyType.getName();
                final ReplyException replyException;
                if (actualReplyTypeName.equals(replyTypeName)) {
                    replyException = new ReplyException("Reply appears to be of the right type (" + replyTypeName + "), but from the wrong classloader (the reply is from classloader " + actualReplyType.getClassLoader() + " but the client expected it to be classloader " + replyType.getClassLoader() + ")");
                } else {
                    replyException = new ReplyException("Reply was of the wrong type (got a " + actualReplyTypeName + "; expected a " + replyTypeName + ")");
                }
                setException(replyException);
                return;
            }
            setResult(actualReply);
        }

        public void handleException(final IOException exception) {
            setException(exception);
        }

        public void handleCancellation() {
            finishCancel();
        }
    }
}
