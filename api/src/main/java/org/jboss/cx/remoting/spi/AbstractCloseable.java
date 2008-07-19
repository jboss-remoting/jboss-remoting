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

import org.jboss.cx.remoting.Closeable;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.spi.SpiUtils;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A basic implementation of a closeable resource.  Use as a convenient base class for your closeable resources.
 * Ensures that the {@code close()} method is idempotent; implements the registry of close handlers.
 */
public abstract class AbstractCloseable<T> implements Closeable<T> {

    private static final Logger log = Logger.getLogger(AbstractCloseable.class);

    protected final Executor executor;
    private final Object closeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private Set<CloseHandler<? super T>> closeHandlers;

    /**
     * Basic constructor.
     *
     * @param executor the executor used to execute the close notification handlers
     */
    protected AbstractCloseable(final Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        this.executor = executor;
    }

    /**
     * Read the status of this resource.  This is just a snapshot in time; there is no guarantee that the resource
     * will remain open for any amount of time, even if this method returns {@code true}.
     *
     * @return {@code true} if the resource is still open
     */
    protected boolean isOpen() {
        return ! closed.get();
    }

    /**
     * Called exactly once when the {@code close()} method is invoked; the actual close operation should take place here.
     *
     * @throws RemotingException if the close failed
     */
    protected void closeAction() throws RemotingException {}

    /**
     * {@inheritDoc}
     */
    public final void close() throws RemotingException {
        if (! closed.getAndSet(true)) {
            log.trace("Closed %s", this);
            synchronized (closeLock) {
                if (closeHandlers != null) {
                    for (final CloseHandler<? super T> handler : closeHandlers) {
                        executor.execute(new Runnable() {
                            @SuppressWarnings({ "unchecked" })
                            public void run() {
                                SpiUtils.safeHandleClose(handler, (T) AbstractCloseable.this);
                            }
                        });
                    }
                    closeHandlers = null;
                }
            }
            closeAction();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addCloseHandler(final CloseHandler<? super T> handler) {
        synchronized (closeLock) {
            if (closeHandlers == null) {
                closeHandlers = new HashSet<CloseHandler<? super T>>();
            }
            closeHandlers.add(handler);
        }
    }

    /**
     * Get the executor to use for handler invocation.
     *
     * @return the executor
     */
    protected Executor getExecutor() {
        return executor;
    }

    /**
     * Finalize this closeable instance.  If the instance hasn't been closed, it is closed and a warning is logged.
     */
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            if (isOpen()) {
                log.warn("Leaked a %s instance!", getClass().getName());
                IoUtils.safeClose(this);
            }
        }
    }
}
