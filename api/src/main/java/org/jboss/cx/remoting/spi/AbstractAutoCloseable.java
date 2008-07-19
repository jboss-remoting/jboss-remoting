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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.spi.remote.Handle;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public abstract class AbstractAutoCloseable<T> extends AbstractCloseable<T> {

    private final AtomicInteger refcount = new AtomicInteger(0);
    private final Executor executor;

    private static final Logger log = Logger.getLogger(AbstractAutoCloseable.class);

    protected AbstractAutoCloseable(final Executor executor) {
        super(executor);
        this.executor = executor;
    }

    protected void safeDec() {
        try {
            dec();
        } catch (Throwable t) {
            log.trace("Failed to decrement reference count: %s", t);
        }
    }

    protected void dec() throws RemotingException {
        final int v = refcount.decrementAndGet();
        if (v == 0) {
            // we dropped the refcount to zero
            log.trace("Refcount of %s dropped to zero, closing", this);
            if (refcount.compareAndSet(0, -65536)) {
                // we are closing
                close();
            }
            // someone incremented it in the meantime... lucky them
        } else if (v < 0) {
            // was already closed; put the count back
            refcount.incrementAndGet();
        } else {
            log.trace("Clearing reference to %s to %d", this, Integer.valueOf(v));
        }
        // otherwise, the resource remains open
    }

    protected void inc() throws RemotingException {
        final int v = refcount.getAndIncrement();
        log.trace("Adding reference to %s to %d", this, Integer.valueOf(v + 1));
        if (v < 0) {
            // was already closed
            refcount.decrementAndGet();
            throw new RemotingException("Resource is closed");
        }
    }

    public Handle<T> getHandle() throws RemotingException {
        return new HandleImpl();
    }

    private final class HandleImpl extends AbstractCloseable<Handle<T>> implements Handle<T> {
        private HandleImpl() throws RemotingException {
            super(AbstractAutoCloseable.this.executor);
            inc();
        }

        protected void closeAction() throws RemotingException {
            dec();
        }

        @SuppressWarnings({ "unchecked" })
        public T getResource() {
            return (T) AbstractAutoCloseable.this;
        }
    }
}
