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

package org.jboss.remoting3;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.marshalling.ClassTable;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.service.classtable.ClassLookupRequest;
import org.jboss.remoting3.service.classtable.ClassLookupResponse;

final class RemoteClassTable implements ClassTable {
    private final Client<ClassLookupRequest, ClassLookupResponse> resolver;
    private final ClientListener<ClassLookupRequest, ClassLookupResponse> clientListener = new RctClientListener();
    private final RequestListener<ClassLookupRequest, ClassLookupResponse> requestListener = new RctRequestListener();

    private final ConcurrentMap<Integer, FutureClass> remoteClasses = null;
    private final ConcurrentMap<Integer, Class<?>> localClasses = null;
    private final ConcurrentMap<Class<?>, IntWriter> localClassWriters = null;

    @SuppressWarnings({ "UnusedDeclaration" })
    private volatile int seq;

    private static final AtomicIntegerFieldUpdater<RemoteClassTable> seqUpdater = AtomicIntegerFieldUpdater.newUpdater(RemoteClassTable.class, "seq");

    public RemoteClassTable(final Client<ClassLookupRequest, ClassLookupResponse> resolver) {
        this.resolver = resolver;
    }

    public ClientListener<ClassLookupRequest, ClassLookupResponse> getClientListener() {
        return clientListener;
    }

    public Writer getClassWriter(final Class<?> clazz) throws IOException {
        IntWriter writer = localClassWriters.get(clazz);
        if (writer == null) {
            final int id = seqUpdater.addAndGet(this, 97);
            writer = new IntWriter(id);
            IntWriter appearing = localClassWriters.putIfAbsent(clazz, writer);
            if (appearing != null) {
                return appearing;
            }
            localClasses.put(Integer.valueOf(id), clazz);
        }
        return writer;
    }

    public Class<?> readClass(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
        final int id = unmarshaller.readInt();
        final Integer idKey = Integer.valueOf(id);
        FutureClass futureClass = remoteClasses.get(idKey);
        if (futureClass != null) {
            return futureClass.getValue();
        }
        futureClass = new FutureClass();
        FutureClass appearing = remoteClasses.putIfAbsent(idKey, futureClass);
        if (appearing != null) {
            return appearing.getValue();
        }
        try {
            final ClassLookupResponse response = resolver.invoke(new ClassLookupRequest(id));
            futureClass.setValue(response.getResolvedClass());
            return response.getResolvedClass();
        } catch (RemoteExecutionException e) {
            throw new ClassNotFoundException(e.getMessage());
        }
    }

    private static final class FutureClass {
        private Class<?> value;
        private boolean done;

        public Class<?> getValue() throws IOException, ClassNotFoundException {
            synchronized (this) {
                while (! done) try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException();
                }
                final Class<?> result = value;
                if (result == null) {
                    throw new ClassNotFoundException();
                }
                return result;
            }
        }

        public void setValue(final Class<?> value) {
            synchronized (this) {
                this.value = value;
                done = true;
                notifyAll();
            }
        }

        public boolean isDone() {
            synchronized (this) {
                return done;
            }
        }

        public void setDone(final boolean done) {
            synchronized (this) {
                this.done = done;
                notifyAll();
            }
        }
    }

    private static final class IntWriter implements Writer {
        private final int value;

        private IntWriter(final int value) {
            this.value = value;
        }

        public void writeClass(final Marshaller marshaller, final Class<?> clazz) throws IOException {
            marshaller.writeInt(value);
        }

        public int getValue() {
            return value;
        }
    }

    private class RctClientListener implements ClientListener<ClassLookupRequest, ClassLookupResponse> {
        public RequestListener<ClassLookupRequest, ClassLookupResponse> handleClientOpen(final ClientContext clientContext) {
            return requestListener;
        }
    }

    private class RctRequestListener implements RequestListener<ClassLookupRequest, ClassLookupResponse> {
        public void handleRequest(final RequestContext<ClassLookupResponse> requestContext, final ClassLookupRequest request) throws RemoteExecutionException {
            try {
                final int id = request.getId();
                final Integer idObj = Integer.valueOf(id);
                final Class<?> localClass = localClasses.get(idObj);
                if (localClass != null) {
                    requestContext.sendReply(new ClassLookupResponse(localClass));
                } else {
                    requestContext.sendFailure("No class found with an ID of #" + idObj, null);
                }
            } catch (IOException e) {
                // no action necessary.
            }
        }
    }
}
