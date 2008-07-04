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
 *
 */
public abstract class AbstractCloseable<T> implements Closeable<T> {

    private static final Logger log = Logger.getLogger(AbstractCloseable.class);

    protected final Executor executor;
    private final Object closeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private Set<CloseHandler<? super T>> closeHandlers;

    protected AbstractCloseable(final Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        this.executor = executor;
    }

    protected boolean isOpen() {
        return ! closed.get();
    }

    public void close() throws RemotingException {
        if (! closed.getAndSet(true)) {
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
        }
    }

    public void addCloseHandler(final CloseHandler<? super T> handler) {
        synchronized (closeLock) {
            if (closeHandlers == null) {
                closeHandlers = new HashSet<CloseHandler<? super T>>();
            }
            closeHandlers.add(handler);
        }
    }

    protected Executor getExecutor() {
        return executor;
    }

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
