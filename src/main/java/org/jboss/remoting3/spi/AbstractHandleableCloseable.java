/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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
import java.io.InterruptedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.HandleableCloseable;
import org.jboss.remoting3.NotOpenException;
import org.jboss.remoting3.RemotingException;
import org.xnio.IoUtils;
import org.jboss.logging.Logger;

/**
 * A basic implementation of a closeable resource.  Use as a convenient base class for your closeable resources.
 * Ensures that the {@code close()} method is idempotent; implements the registry of close handlers.
 *
 * @param <T> the type of the closeable resource
 */
public abstract class AbstractHandleableCloseable<T extends HandleableCloseable<T>> implements HandleableCloseable<T> {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.resource");
    private static final boolean LEAK_DEBUGGING;

    private final Executor executor;
    private final StackTraceElement[] backtrace;
    private final boolean autoClose;

    private final Object closeLock = new Object();
    private State state = State.OPEN;
    private Map<Key, CloseHandler<? super T>> closeHandlers = null;

    enum State {
        OPEN,
        CLOSING,
        CLOSED,
    }

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
        this(executor, true);
    }

    /**
     * Basic constructor.
     *
     * @param executor the executor used to execute the close notification handlers
     * @param autoClose {@code true} if this instance should automatically close on finalize
     */
    protected AbstractHandleableCloseable(final Executor executor, final boolean autoClose) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        this.executor = executor;
        backtrace = LEAK_DEBUGGING ? new Throwable().getStackTrace() : null;
        this.autoClose = autoClose;
    }

    /**
     * Read the status of this resource.  This is just a snapshot in time; there is no guarantee that the resource
     * will remain open for any amount of time, even if this method returns {@code true}.
     *
     * @return {@code true} if the resource is still open
     */
    protected boolean isOpen() {
        synchronized (closeLock) {
            return state == State.OPEN;
        }
    }

    protected boolean isClosing() {
        synchronized (closeLock) {
            return state == State.CLOSING;
        }
    }

    /**
     * Called exactly once when the {@code close()} method is invoked; the actual close operation should take place here.
     * This method <b>must</b> call {@link #closeComplete()}, directly or indirectly, for the close operation to finish
     * (it may happen in another thread but it must happen).
     *
     * @throws RemotingException if the close failed
     */
    protected void closeAction() throws IOException {
        closeComplete();
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        log.tracef("Closing %s synchronously", this);
        boolean first = false;
        synchronized (closeLock) {
            switch (state) {
                case OPEN: {
                    first = true;
                    state = State.CLOSING;
                    break;
                }
                case CLOSING: {
                    break;
                }
                case CLOSED: return;
                default: throw new IllegalStateException();
            }
        }
        if (first) try {
            closeAction();
        } catch (IOException e) {
            log.tracef(e, "Close of %s failed", this);
            final Map<Key, CloseHandler<? super T>> closeHandlers;
            synchronized (closeLock) {
                state = State.CLOSED;
                closeHandlers = this.closeHandlers;
                this.closeHandlers = null;
                closeLock.notifyAll();
            }
            if (closeHandlers != null) {
                for (final CloseHandler<? super T> handler : closeHandlers.values()) {
                    runCloseTask(executor, new CloseHandlerTask(handler, e));
                }
            }
            throw e;
        } catch (Throwable t) {
            log.errorf(t, "Close action for %s failed to execute (resource may be left in an indeterminate state)", this);
            throw new IllegalStateException(t);
        }
        final SyncCloseHandler<T> syncCloseHandler = new SyncCloseHandler<T>();
        final Key key = addCloseHandler(syncCloseHandler);
        try {
            synchronized (syncCloseHandler) {
                while (! syncCloseHandler.done) {
                    try {
                        syncCloseHandler.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("Interrupted while waiting for close to complete");
                    }
                }
                IOException cause = syncCloseHandler.cause;
                if (cause != null) {
                    final IOException clone = clone(cause);
                    if (cause != clone) {
                        SpiUtils.glueStackTraces(cause, Thread.currentThread().getStackTrace(), 1, "asynchronous close");
                    }
                    throw clone;
                }
                return;
            }
        } finally {
            key.remove();
        }
    }

    private static <T extends IOException> T clone(T original) {
        final Throwable cause = original.getCause();
        @SuppressWarnings("unchecked")
        final Class<T> originalClass = (Class<T>) original.getClass();
        // try a few constructors
        Constructor<T> constructor = null;
        try {
            constructor = originalClass.getConstructor(String.class, Throwable.class);
            final T clone = constructor.newInstance(original.getMessage(), cause);
            clone.setStackTrace(original.getStackTrace());
            return clone;
        } catch (NoSuchMethodException e) {
            // nope
        } catch (InvocationTargetException e) {
            // nope
        } catch (InstantiationException e) {
            // nope
        } catch (IllegalAccessException e) {
            // nope
        }
        try {
            constructor = originalClass.getConstructor(String.class);
            final T clone = constructor.newInstance(original.getMessage());
            clone.initCause(cause);
            clone.setStackTrace(original.getStackTrace());
            return clone;
        } catch (NoSuchMethodException e) {
            // nope
        } catch (InvocationTargetException e) {
            // nope
        } catch (InstantiationException e) {
            // nope
        } catch (IllegalAccessException e) {
            // nope
        }
        try {
            constructor = originalClass.getConstructor();
            final T clone = constructor.newInstance();
            clone.initCause(cause);
            clone.setStackTrace(original.getStackTrace());
            return clone;
        } catch (NoSuchMethodException e) {
            // nope
        } catch (InvocationTargetException e) {
            // nope
        } catch (InstantiationException e) {
            // nope
        } catch (IllegalAccessException e) {
            // nope
        }
        // we tried!
        return original;
    }

    /**
     * Call when close is complete.
     */
    protected void closeComplete() {
        final Map<Key, CloseHandler<? super T>> closeHandlers;
        synchronized (closeLock) {
            switch (state) {
                case OPEN: {
                    log.tracef("Closing %s asynchronously", this);
                    // fall thru
                }
                case CLOSING: {
                    log.tracef("Completed close of %s", this);
                    state = State.CLOSED;
                    closeHandlers = this.closeHandlers;
                    this.closeHandlers = null;
                    break;
                }
                case CLOSED: {
                    // idempotent
                    return;
                }
                default:
                    throw new IllegalStateException();
            }
            closeLock.notifyAll();
        }
        if (closeHandlers != null) {
            for (final CloseHandler<? super T> handler : closeHandlers.values()) {
                runCloseTask(executor, new CloseHandlerTask(handler, null));
            }
        }
    }

    /**
     * Call if an async close has failed.
     *
     * @param cause the failure cause
     */
    protected void closeFailed(IOException cause) {
        final Map<Key, CloseHandler<? super T>> closeHandlers;
        synchronized (closeLock) {
            switch (state) {
                case CLOSING: {
                    log.tracef(cause, "Completed close of %s with failure", this);
                    state = State.CLOSED;
                    closeHandlers = this.closeHandlers;
                    this.closeHandlers = null;
                    break;
                }
                case CLOSED: {
                    // idempotent
                    return;
                }
                default:
                    throw new IllegalStateException();
            }
            closeLock.notifyAll();
        }
        if (closeHandlers != null) {
            for (final CloseHandler<? super T> handler : closeHandlers.values()) {
                runCloseTask(executor, new CloseHandlerTask(handler, cause));
            }
        }
    }

    /** {@inheritDoc} */
    public void awaitClosed() throws InterruptedException {
        synchronized (closeLock) {
            while (state != State.CLOSED) {
                closeLock.wait();
            }
        }
    }

    /** {@inheritDoc} */
    public void awaitClosedUninterruptibly() {
        boolean intr = false;
        try {
            synchronized (closeLock) {
                while (state != State.CLOSED) {
                    try {
                        closeLock.wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
            }
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }

    /** {@inheritDoc} */
    public void closeAsync() {
        log.tracef("Closing %s asynchronously", this);
        synchronized (closeLock) {
            switch (state) {
                case OPEN: {
                    state = State.CLOSING;
                    break;
                }
                case CLOSING:
                case CLOSED: return;
                default: throw new IllegalStateException();
            }
        }
        try {
            closeAction();
        } catch (IOException e) {
            log.tracef(e, "Close of %s failed", this);
            final Map<Key, CloseHandler<? super T>> closeHandlers;
            synchronized (closeLock) {
                state = State.CLOSED;
                closeHandlers = this.closeHandlers;
                this.closeHandlers = null;
                closeLock.notifyAll();
            }
            if (closeHandlers != null) {
                for (final CloseHandler<? super T> handler : closeHandlers.values()) {
                    runCloseTask(executor, new CloseHandlerTask(handler, e));
                }
            }
        } catch (Throwable t) {
            log.errorf(t, "Close action for %s failed to execute (resource may be left in an indeterminate state)", this);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Key addCloseHandler(final CloseHandler<? super T> handler) {
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        synchronized (closeLock) {
            if (state == State.OPEN || state == State.CLOSING) {
                final Key key = new KeyImpl<T>(this);
                final Map<Key, CloseHandler<? super T>> closeHandlers = this.closeHandlers;
                if (closeHandlers == null) {
                    final IdentityHashMap<Key, CloseHandler<? super T>> newMap = new IdentityHashMap<Key, CloseHandler<? super T>>();
                    this.closeHandlers = newMap;
                    newMap.put(key, handler);
                } else {
                    closeHandlers.put(key, handler);
                }
                return key;
            }
        }
        runCloseTask(executor, new CloseHandlerTask(handler, null));
        return new NullKey();
    }

    private static void runCloseTask(final Executor executor, final Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ree) {
            task.run();
        }
    }

    private static final class NullKey implements Key {
        public void remove() {
        }
    }

    private static final class KeyImpl<T extends HandleableCloseable<T>> implements Key {

        private final AbstractHandleableCloseable<T> instance;

        private KeyImpl(final AbstractHandleableCloseable<T> instance) {
            this.instance = instance;
        }

        public void remove() {
            synchronized (instance.closeLock) {
                final Map<Key, CloseHandler<? super T>> closeHandlers = instance.closeHandlers;

                if (closeHandlers != null) {
                    closeHandlers.remove(this);
                }
            }
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
            if (autoClose && isOpen()) {
                if (LEAK_DEBUGGING) {
                    final Throwable t = new LeakThrowable();
                    t.setStackTrace(backtrace);
                    log.warnf(t, "Leaked a %s instance: %s", getClass().getName(), this);
                } else {
                    log.tracef("Leaked a %s instance: %s", getClass().getName(), this);
                }
                IoUtils.safeClose(this);
            }
        }
    }

    /**
     * Check if open, throwing an exception if it is not.
     *
     * @throws NotOpenException if not open
     */
    protected void checkOpen() throws NotOpenException {
        synchronized (closeLock) {
            if (state != State.OPEN) {
                throw new NotOpenException(toString() + " is not open");
            }
        }
    }

    @SuppressWarnings({ "serial" })
    static final class LeakThrowable extends Throwable {

        LeakThrowable() {
        }

        public String toString() {
            return "a leaked reference";
        }
    }

    final class CloseHandlerTask implements Runnable {

        private final CloseHandler<? super T> handler;
        private final IOException exception;

        CloseHandlerTask(final CloseHandler<? super T> handler, final IOException exception) {
            this.handler = handler;
            this.exception = exception;
        }

        @SuppressWarnings("unchecked")
        public void run() {
            SpiUtils.safeHandleClose(handler, (T) AbstractHandleableCloseable.this, exception);
        }
    }

    private class SyncCloseHandler<T extends HandleableCloseable<T>> implements CloseHandler<T> {

        boolean done;
        IOException cause;

        public void handleClose(final T closed, final IOException exception) {
            synchronized (this) {
                done = true;
                cause = exception;
                notifyAll();
            }
        }
    }
}
