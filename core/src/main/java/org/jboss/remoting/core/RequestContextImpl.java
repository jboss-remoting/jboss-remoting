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

package org.jboss.remoting.core;

import org.jboss.remoting.RequestContext;
import org.jboss.remoting.ClientContext;
import org.jboss.remoting.RequestCancelHandler;
import org.jboss.remoting.RemoteExecutionException;
import org.jboss.remoting.RemoteReplyException;
import org.jboss.remoting.IndeterminateOutcomeException;
import org.jboss.remoting.spi.remote.ReplyHandler;
import org.jboss.remoting.spi.SpiUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.HashSet;
import java.io.IOException;

/**
 *
 */
public final class RequestContextImpl<O> implements RequestContext<O> {

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object cancelLock = new Object();
    private final ReplyHandler replyHandler;
    private final ClientContextImpl clientContext;
    private final AtomicInteger taskCount = new AtomicInteger();

    // @protectedby cancelLock
    private boolean cancelled;
    // @protectedby cancelLock
    private Set<RequestCancelHandler<O>> cancelHandlers;
    private final RequestListenerExecutor executor;

    RequestContextImpl(final ReplyHandler replyHandler, final ClientContextImpl clientContext) {
        this.replyHandler = replyHandler;
        this.clientContext = clientContext;
        //noinspection ThisEscapedInObjectConstruction
        executor = new RequestListenerExecutor(clientContext.getExecutor(), this);
    }

    public ClientContext getContext() {
        return clientContext;
    }

    public boolean isCancelled() {
        synchronized (cancelLock) {
            return cancelled;
        }
    }

    public void sendReply(final O reply) throws IOException, IllegalStateException {
        if (! closed.getAndSet(true)) {
            try {
                replyHandler.handleReply(reply);
            } catch (IOException e) {
                SpiUtils.safeHandleException(replyHandler, new RemoteReplyException("Remote reply failed", e));
                throw e;
            }
        } else {
            throw new IllegalStateException("Reply already sent");
        }
    }

    public void sendFailure(final String msg, final Throwable cause) throws IOException, IllegalStateException {
        if (! closed.getAndSet(true)) {
            replyHandler.handleException(new RemoteExecutionException(msg, cause));
        } else {
            throw new IllegalStateException("Reply already sent");
        }
    }

    public void sendCancelled() throws IOException, IllegalStateException {
        if (! closed.getAndSet(true)) {
            try {
                replyHandler.handleCancellation();
            } catch (IOException e) {
                // this is highly unlikely to succeed
                SpiUtils.safeHandleException(replyHandler, new RemoteReplyException("Remote cancellation acknowledgement failed", e));
            }
        } else {
            throw new IllegalStateException("Reply already sent");
        }
    }

    public void addCancelHandler(final RequestCancelHandler<O> handler) {
        synchronized (cancelLock) {
            if (cancelled) {
                SpiUtils.safeNotifyCancellation(handler, this);
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

    protected void cancel() {
        synchronized (cancelLock) {
            if (! cancelled) {
                cancelled = true;
                if (cancelHandlers != null) {
                    for (final RequestCancelHandler<O> handler : cancelHandlers) {
                        executor.execute(new Runnable() {
                            public void run() {
                                SpiUtils.safeNotifyCancellation(handler, RequestContextImpl.this);
                            }
                        });
                    }
                    cancelHandlers = null;
                }
                executor.interruptAll();
            }
        }
    }

    void startTask() {
        taskCount.incrementAndGet();
    }

    void finishTask() {
        if (taskCount.decrementAndGet() == 0 && closed.getAndSet(true)) {
            // no response sent!  send back IndeterminateOutcomeException
            SpiUtils.safeHandleException(replyHandler, new IndeterminateOutcomeException("No reply was sent by the request listener"));
        }
    }
}
