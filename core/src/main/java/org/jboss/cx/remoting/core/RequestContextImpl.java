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

package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.ClientContext;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestCancelHandler;
import org.jboss.cx.remoting.core.util.TaggingExecutor;
import org.jboss.cx.remoting.spi.remote.ReplyHandler;
import org.jboss.cx.remoting.spi.SpiUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.HashSet;

/**
 *
 */
public final class RequestContextImpl<O> implements RequestContext<O> {

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object cancelLock = new Object();
    private final ReplyHandler<O> replyHandler;
    private final ClientContextImpl clientContext;

    private final AtomicBoolean cancelled = new AtomicBoolean();
    // @protectedby cancelLock
    private Set<RequestCancelHandler<O>> cancelHandlers;
    private final TaggingExecutor executor;

    RequestContextImpl(final ReplyHandler<O> replyHandler, final ClientContextImpl clientContext) {
        this.replyHandler = replyHandler;
        this.clientContext = clientContext;
        executor = new TaggingExecutor(clientContext.getExecutor());
    }

    RequestContextImpl(final ClientContextImpl clientContext) {
        this.clientContext = clientContext;
        executor = new TaggingExecutor(clientContext.getExecutor());
        replyHandler = null;
    }

    public ClientContext getContext() {
        return clientContext;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void sendReply(final O reply) throws RemotingException, IllegalStateException {
        if (! closed.getAndSet(true)) {
            if (replyHandler != null) {
                replyHandler.handleReply(reply);
            }
        } else {
            throw new IllegalStateException("Reply already sent");
        }
    }

    public void sendFailure(final String msg, final Throwable cause) throws RemotingException, IllegalStateException {
        if (! closed.getAndSet(true)) {
            if (replyHandler != null) {
                replyHandler.handleException(msg, cause);
            }
        } else {
            throw new IllegalStateException("Reply already sent");
        }
    }

    public void sendCancelled() throws RemotingException, IllegalStateException {
        if (! closed.getAndSet(true)) {
            if (replyHandler != null) {
                replyHandler.handleCancellation();
            }
        } else {
            throw new IllegalStateException("Reply already sent");
        }
    }

    public void addCancelHandler(final RequestCancelHandler<O> handler) {
        synchronized (cancelLock) {
            if (cancelled.get()) {
                SpiUtils.safeNotifyCancellation(handler, this, false);
            } else {
                if (cancelHandlers == null) {
                    cancelHandlers = new HashSet<RequestCancelHandler<O>>();
                }
                cancelHandlers.add(handler);
            }
        }
    }

    public void execute(final Runnable command) {
        executor.execute(command);
    }

    protected void cancel(final boolean mayInterrupt) {
        if (! cancelled.getAndSet(true)) {
            synchronized (cancelLock) {
                if (cancelHandlers != null) {
                    for (final RequestCancelHandler<O> handler : cancelHandlers) {
                        executor.execute(new Runnable() {
                            public void run() {
                                SpiUtils.safeNotifyCancellation(handler, RequestContextImpl.this, mayInterrupt);
                            }
                        });
                    }
                    cancelHandlers = null;
                }
            }
            if (mayInterrupt) {
                executor.interruptAll();
            }
        }
    }
}
