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

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.spi.remote.Handle;
import org.jboss.xnio.log.Logger;

/**
 * A closeable implementation that supports reference counting.  Since the initial reference count is zero, implementors
 * must be careful to ensure that the first operation invoked is a call to {@link #getHandle()}.
 */
public abstract class AbstractAutoCloseable<T> extends AbstractHandleableCloseable<T> {

    private final AtomicInteger refcount = new AtomicInteger(0);
    private final Executor executor;

    private static final Logger log = Logger.getLogger(AbstractAutoCloseable.class);

    /**
     * Basic constructor.
     *
     * @param executor the executor used to execute the close notification handlers
     */
    protected AbstractAutoCloseable(final Executor executor) {
        super(executor);
        this.executor = executor;
    }

    /**
     * Decrement the reference count by one.  If the count drops to zero, the resource is closed.
     *
     * @throws IOException if the reference count dropped to zero and the close operation threw an exception
     */
    protected void dec() throws IOException {
        final int v = refcount.decrementAndGet();
        if (v == 0) {
            // we dropped the refcount to zero
            log.trace("Lowering reference count of %s to 0 (closing)", this);
            if (refcount.compareAndSet(0, -65536)) {
                // we are closing
                close();
            }
            // someone incremented it in the meantime... lucky them
        } else if (v < 0) {
            // was already closed; put the count back
            refcount.incrementAndGet();
        } else {
            log.trace("Lowering reference count of %s to %d", this, Integer.valueOf(v));
        }
        // otherwise, the resource remains open
    }

    /**
     * Increment the reference count by one.  If the resource is closed, an exception is thrown.
     *
     * @throws RemotingException if the resource is closed
     */
    protected void inc() throws IOException {
        final int v = refcount.getAndIncrement();
        log.trace("Raising reference count of %s to %d", this, Integer.valueOf(v + 1));
        if (v < 0) {
            // was already closed
            refcount.decrementAndGet();
            throw new IOException("Resource is closed");
        }
    }

    /**
     * Get a handle to this resource.  Increments the reference count by one.  If the resource is closed, an exception
     * is thrown.
     *
     * @return the handle
     * @throws RemotingException if the resource is closed
     */
    public Handle<T> getHandle() throws IOException {
        return new HandleImpl();
    }

    private final class HandleImpl extends AbstractHandleableCloseable<Handle<T>> implements Handle<T> {
        private HandleImpl() throws IOException {
            super(AbstractAutoCloseable.this.executor);
            inc();
        }

        protected void closeAction() throws IOException {
            dec();
        }

        @SuppressWarnings({ "unchecked" })
        public T getResource() {
            return (T) AbstractAutoCloseable.this;
        }

        public String toString() {
            return "handle <" + Integer.toString(hashCode(), 16) + "> to " + String.valueOf(AbstractAutoCloseable.this);
        }
    }

    public String toString() {
        return "generic resource <" + Integer.toString(hashCode(), 16) + ">";
    }
}
