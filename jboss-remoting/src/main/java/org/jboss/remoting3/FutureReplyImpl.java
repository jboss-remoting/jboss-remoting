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
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.xnio.AbstractIoFuture;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoFuture;

/**
 *
 */
final class FutureReplyImpl<O> extends AbstractIoFuture<O> {

    private final Executor executor;
    private final Checker<? extends O> checker;
    private final ReplyHandler replyHandler = new Handler();
    private volatile Cancellable remoteRequestContext;

    FutureReplyImpl(final Executor executor, final Checker<? extends O> checker) {
        this.executor = executor;
        this.checker = checker;
    }

    FutureReplyImpl(final Executor executor, final Class<? extends O> expectedType) {
        this(executor, new Checker<O>() {
            public O cast(final Object input) {
                return expectedType.cast(input);
            }
        });
    }

    FutureReplyImpl(final Executor executor, final TypedRequest<?, ? extends O> typedRequest) {
        this(executor, new Checker<O>() {
            public O cast(final Object input) {
                return typedRequest.castReply(input);
            }
        });
    }

    void setRemoteRequestContext(final Cancellable remoteRequestContext) {
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

    interface Checker<O> {
        O cast(Object input);
    }

    private final class Handler implements ReplyHandler {

        public void handleReply(final Object reply) {
            final Checker<? extends O> checker = FutureReplyImpl.this.checker;
            final O actualReply;
            try {
                actualReply = checker.cast(reply);
            } catch (ClassCastException e) {
                // reply can't be null, else we wouldn't be here...
                setException(new ReplyException("Reply was of unexpected type " + reply.getClass().getName()));
                return;
            }
            setResult(actualReply);
        }

        public void handleException(final IOException exception) {
            setException(exception);
        }

        public void handleCancellation() {
            setCancelled();
        }
    }
}
