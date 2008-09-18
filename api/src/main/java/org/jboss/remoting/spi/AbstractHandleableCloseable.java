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

import org.jboss.remoting.HandleableCloseable;
import org.jboss.remoting.RemotingException;
import org.jboss.remoting.CloseHandler;
import org.jboss.remoting.spi.SpiUtils;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.io.IOException;

/**
 * A basic implementation of a closeable resource.  Use as a convenient base class for your closeable resources.
 * Ensures that the {@code close()} method is idempotent; implements the registry of close handlers.
 */
public abstract class AbstractHandleableCloseable<T> implements HandleableCloseable<T> {

    private static final Logger log = Logger.getLogger(AbstractHandleableCloseable.class);

    protected final Executor executor;
    private final Object closeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private Set<CloseHandler<? super T>> closeHandlers;

    private static final boolean LEAK_DEBUGGING;
    private final StackTraceElement[] backtrace;

    static {
        boolean b = false;
        try {
            b = Boolean.parseBoolean(AccessController.doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    return System.getProperty("jboss.remoting.leakdebugging", "false");
                }
            }));
        } catch (SecurityException se) {
            b = false;
        }
        LEAK_DEBUGGING = b;
    }

    /**
     * Basic constructor.
     *
     * @param executor the executor used to execute the close notification handlers
     */
    protected AbstractHandleableCloseable(final Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        this.executor = executor;
        backtrace = LEAK_DEBUGGING ? new Throwable().getStackTrace() : null;
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
    protected void closeAction() throws IOException {}

    /**
     * {@inheritDoc}
     */
    public final void close() throws IOException {
        if (! closed.getAndSet(true)) {
            log.trace("Closed %s", this);
            synchronized (closeLock) {
                if (closeHandlers != null) {
                    for (final CloseHandler<? super T> handler : closeHandlers) {
                        try {
                            executor.execute(new Runnable() {
                                @SuppressWarnings({ "unchecked" })
                                public void run() {
                                    SpiUtils.safeHandleClose(handler, (T) AbstractHandleableCloseable.this);
                                }
                            });
                        } catch (RejectedExecutionException ree) {
                            log.warn("Unable to execute close handler (execution rejected) for %s (%s)", this, ree.getMessage());
                        }
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
                if (LEAK_DEBUGGING) {
                    final Throwable t = new LeakThrowable();
                    t.setStackTrace(backtrace);
                    log.warn(t, "Leaked a %s instance: %s", getClass().getName(), this);
                } else {
                    log.warn("Leaked a %s instance: %s", getClass().getName(), this);
                }
                IoUtils.safeClose(this);
            }
        }
    }

    @SuppressWarnings({ "serial" })
    private static final class LeakThrowable extends Throwable {

        public LeakThrowable() {
        }

        public String toString() {
            return "a leaked reference";
        }
    }
}
