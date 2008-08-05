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

package org.jboss.cx.remoting.protocol.basic;

import org.jboss.cx.remoting.spi.remote.RequestHandlerSource;
import org.jboss.cx.remoting.spi.remote.Handle;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.xnio.IoUtils;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Iterator;
import java.io.IOException;

/**
 *
 */
public final class ServiceRegistryImpl implements ServiceRegistry {

    private static final int START = 32768;

    private final ConcurrentMap<Integer, Handle<RequestHandlerSource>> map = CollectionUtil.concurrentMap();
    private final AtomicInteger dynamicSequence = new AtomicInteger(START);
    private final ServiceRegistry parent;

    public ServiceRegistryImpl(final ServiceRegistry parent) {
        this.parent = parent;
    }

    public ServiceRegistryImpl() {
        parent = null;
    }

    public int bind(final RequestHandlerSource requestHandlerSource) throws IOException {
        final Handle<RequestHandlerSource> handle = requestHandlerSource.getHandle();
        boolean ok = false;
        try {
            for (;;) {
                final int seqVal = dynamicSequence.getAndIncrement();
                if (seqVal < 0) {
                    dynamicSequence.compareAndSet(seqVal, START);
                    continue;
                }
                if (map.putIfAbsent(Integer.valueOf(seqVal), handle) != null) {
                    ok = true;
                    return seqVal;
                }
            }
        } finally {
            if (! ok) {
                IoUtils.safeClose(handle);
            }
        }
    }

    public void bind(final RequestHandlerSource requestHandlerSource, final int id) throws IOException {
        final Handle<RequestHandlerSource> handle = requestHandlerSource.getHandle();
        boolean ok = false;
        try {
            if (map.putIfAbsent(Integer.valueOf(id), handle) != null) {
                throw new RemotingException("Service already bound to that ID");
            }
            ok = true;
        } finally {
            if (! ok) {
                IoUtils.safeClose(handle);
            }
        }
    }

    public void unbind(final int id) throws RemotingException {
        map.remove(Integer.valueOf(id));
    }

    public void clear() {
        Iterator<Handle<RequestHandlerSource>> it = map.values().iterator();
        while (it.hasNext()) {
            IoUtils.safeClose(it.next());
            it.remove();
        }
    }

    public Handle<RequestHandlerSource> lookup(final int id) throws IOException {
        final Handle<RequestHandlerSource> handle = map.get(Integer.valueOf(id));
        return handle != null || parent == null ? handle.getResource().getHandle() : parent.lookup(id);
    }

    protected void finalize() throws Throwable {
        clear();
        super.finalize();
    }
}
