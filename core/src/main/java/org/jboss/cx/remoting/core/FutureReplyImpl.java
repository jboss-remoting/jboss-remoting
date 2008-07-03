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

import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RequestCompletionHandler;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.spi.remote.ReplyHandler;
import org.jboss.cx.remoting.spi.remote.RemoteRequestContext;
import org.jboss.cx.remoting.spi.SpiUtils;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.List;

/**
 *
 */
public final class FutureReplyImpl<O> implements FutureReply<O> {

    private final Executor executor;
    private final ReplyHandler<O> replyHandler = new Handler();
    private final Object lock = new Object();
    // @protectedby lock
    private State state = State.WAITING;
    // @protectedby lock
    private RemoteRequestContext remoteRequestContext;
    // @protectedby lock
    private O result;
    // @protectedby lock
    private Throwable cause;
    // @protectedby lock
    private String msg;
    // @protectedby lock
    private List<RequestCompletionHandler<O>> completionHandlers;

    public FutureReplyImpl(final Executor executor) {
        this.executor = executor;
    }

    private enum State {
        NEW,
        WAITING,
        DONE,
        CANCELLED,
        FAILED,
    }

    void setRemoteRequestContext(final RemoteRequestContext remoteRequestContext) {
        synchronized (lock) {
            if (state != State.NEW) {
                throw new IllegalStateException("Wrong state");
            }
            state = State.WAITING;
            this.remoteRequestContext = remoteRequestContext;
        }
    }

    public boolean cancel(final boolean mayInterruptIfRunning) {
        final RemoteRequestContext context;
        synchronized (lock) {
            while (state == State.NEW) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    return false;
                }
            }
            context = remoteRequestContext;
        }
        context.cancel(mayInterruptIfRunning);
        synchronized (lock) {
            while (state == State.WAITING) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    return false;
                }
            }
            return state == State.CANCELLED;
        }
    }

    public FutureReply<O> sendCancel(final boolean mayInterruptIfRunning) {
        final RemoteRequestContext context;
        synchronized (lock) {
            while (state == State.NEW) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    return this;
                }
            }
            context = remoteRequestContext;
        }
        context.cancel(mayInterruptIfRunning);
        return this;
    }

    public boolean isCancelled() {
        synchronized (lock) {
            return state == State.CANCELLED;
        }
    }

    public boolean isDone() {
        synchronized (lock) {
            return state == State.DONE;
        }
    }

    public O get() throws CancellationException, RemoteExecutionException {
        boolean intr = false;
        try {
            synchronized (lock) {
                while (state == State.WAITING || state == State.NEW) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
                switch (state) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case DONE:
                        return result;
                    case FAILED:
                        throw new RemoteExecutionException(msg, cause);
                    default:
                        throw new IllegalStateException("Wrong state");
                }
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public O getInterruptibly() throws InterruptedException, CancellationException, RemoteExecutionException {
        synchronized (lock) {
            while (state == State.WAITING || state == State.NEW) {
                lock.wait();
            }
            switch (state) {
                case CANCELLED:
                    throw new CancellationException("Request was cancelled");
                case DONE:
                    return result;
                case FAILED:
                    throw new RemoteExecutionException(msg, cause);
                default:
                    throw new IllegalStateException("Wrong state");
            }
        }
    }

    public O get(final long timeout, final TimeUnit unit) throws CancellationException, RemoteExecutionException {
        if (unit == null) {
            throw new NullPointerException("unit is null");
        }
        if (timeout < 0L) {
            throw new IllegalArgumentException("timeout is negative");
        }
        boolean intr = false;
        try {
            synchronized (lock) {
                long now = System.currentTimeMillis();
                final long deadline = now + unit.toMillis(timeout);
                if (deadline < 0L) {
                    return get();
                }
                while (state == State.WAITING || state == State.NEW) {
                    try {
                        lock.wait(deadline - now);
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                    now = System.currentTimeMillis();
                }
                switch (state) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case DONE:
                        return result;
                    case FAILED:
                        throw new RemoteExecutionException(msg, cause);
                    default:
                        throw new IllegalStateException("Wrong state");
                }
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public O getInterruptibly(final long timeout, final TimeUnit unit) throws InterruptedException, CancellationException, RemoteExecutionException {
        if (unit == null) {
            throw new NullPointerException("unit is null");
        }
        if (timeout < 0L) {
            throw new IllegalArgumentException("timeout is negative");
        }
        synchronized (lock) {
            while (state == State.WAITING || state == State.NEW) {
                unit.timedWait(lock, timeout);
            }
            switch (state) {
                case CANCELLED:
                    throw new CancellationException("Request was cancelled");
                case DONE:
                    return result;
                case FAILED:
                    throw new RemoteExecutionException(msg, cause);
                case WAITING:
                case NEW:
                    return null;
                default:
                    throw new IllegalStateException("Wrong state");
            }
        }
    }

    public FutureReply<O> addCompletionHandler(final RequestCompletionHandler<O> handler) {
        synchronized (lock) {
            switch (state) {
                case NEW:
                case WAITING:
                    if (completionHandlers == null) {
                        completionHandlers = CollectionUtil.arrayList();
                    }
                    completionHandlers.add(handler);
                    break;
                default:
                    SpiUtils.safeHandleRequestCompletion(handler, this);
                    break;
            }
        }
        return this;
    }

    ReplyHandler<O> getReplyHandler() {
        return replyHandler;
    }

    private void runCompletionHandlers() {
        synchronized (lock) {
            final List<RequestCompletionHandler<O>> handlers = completionHandlers;
            if (handlers != null) {
                completionHandlers = null;
                executor.execute(new Runnable() {
                    public void run() {
                        for (RequestCompletionHandler<O> handler : handlers) {
                            SpiUtils.safeHandleRequestCompletion(handler, FutureReplyImpl.this);
                        }
                    }
                });
            }
        }
    }

    private final class Handler implements ReplyHandler<O> {

        public void handleReply(final O reply) {
            synchronized (lock) {
                while (state == State.NEW) {
                    boolean intr = false;
                    try {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    } finally {
                        if (intr) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                if (state == State.WAITING) {
                    state = State.DONE;
                    result = reply;
                    runCompletionHandlers();
                }
            }
        }

        public void handleException(final String exMsg, final Throwable exCause) {
            synchronized (lock) {
                while (state == State.NEW) {
                    boolean intr = false;
                    try {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    } finally {
                        if (intr) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                if (state == State.WAITING) {
                    state = State.FAILED;
                    msg = exMsg;
                    cause = exCause;
                    runCompletionHandlers();
                }
            }
        }

        public void handleCancellation() {
            synchronized (lock) {
                while (state == State.NEW) {
                    boolean intr = false;
                    try {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    } finally {
                        if (intr) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                if (state == State.WAITING) {
                    state = State.CANCELLED;
                    runCompletionHandlers();
                }
            }
        }
    }
}
