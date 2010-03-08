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
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.jboss.marshalling.Pair;
import org.jboss.marshalling.cloner.ClassCloner;
import org.jboss.marshalling.cloner.ClassLoaderClassCloner;
import org.jboss.marshalling.cloner.CloneTable;
import org.jboss.marshalling.cloner.ClonerConfiguration;
import org.jboss.marshalling.cloner.ObjectCloner;
import org.jboss.marshalling.cloner.ObjectClonerFactory;
import org.jboss.marshalling.cloner.ObjectCloners;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.LocalReplyHandler;
import org.jboss.remoting3.spi.LocalRequestHandler;
import org.jboss.remoting3.spi.RemoteRequestHandler;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.remoting3.stream.ObjectSink;
import org.jboss.remoting3.stream.ObjectSource;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;

class LocalRemoteRequestHandler extends AbstractHandleableCloseable<RemoteRequestHandler> implements RemoteRequestHandler {

    private final LocalRequestHandler handler;
    private final ClonerPairSource clonerPairSource;

    public LocalRemoteRequestHandler(final LocalRequestHandler handler, final ClassLoader replyClassLoader, final OptionMap optionMap, final OptionMap defaultOptionMap, final Executor executor) {
        super(executor);
        final boolean callByValue = optionMap.get(RemotingOptions.CALL_BY_VALUE, defaultOptionMap.get(RemotingOptions.CALL_BY_VALUE, false));
        final ClonerPairSource clonerPairSource;
        if (callByValue) {
            handler.addCloseHandler(SpiUtils.closingCloseHandler(LocalRemoteRequestHandler.this));
            final ClonerConfiguration configuration = new ClonerConfiguration();
            final ClassLoader requestClassLoader = handler.getClassLoader();
            clonerPairSource = new HandlerClonerSource(configuration, ObjectCloners.getSerializingObjectClonerFactory(), requestClassLoader, replyClassLoader);
        } else {
            clonerPairSource = ClonerPairSource.IDENTITY;
        }

        this.clonerPairSource = clonerPairSource;
        this.handler = handler;
    }

    protected void closeAction() throws IOException {
        handler.close();
    }

    public Cancellable receiveRequest(final Object request, final LocalReplyHandler replyHandler) {
        final Pair<ObjectCloner, ObjectCloner> pair = clonerPairSource.createPair();
        final ObjectCloner requestCloner = pair.getA();
        final ObjectCloner replyCloner = pair.getB();
        final Object clonedRequest;
        try {
            clonedRequest = requestCloner.clone(request);
        } catch (IOException e) {
            replyHandler.handleException(e);
            return IoUtils.nullCancellable();
        } catch (ClassNotFoundException e) {
            final InvalidObjectException ioe = new InvalidObjectException("Invalid object: " + e);
            ioe.initCause(e);
            replyHandler.handleException(ioe);
            return IoUtils.nullCancellable();
        }
        return handler.receiveRequest(clonedRequest, new LocalRemoteReplyHandler(replyHandler, replyCloner));
    }

    private static class LocalCloneTable implements CloneTable {

        private volatile ObjectCloner inboundCloner;

        LocalCloneTable() {
        }

        void setInboundCloner(final ObjectCloner inboundCloner) {
            this.inboundCloner = inboundCloner;
        }

        public Object clone(final Object original, final ObjectCloner outboundCloner, final ClassCloner classCloner) throws IOException, ClassNotFoundException {
            if (original instanceof ObjectSource) {
                return new CloningObjectSource((ObjectSource) original, outboundCloner);
            } else if (original instanceof ObjectSink) {
                return new CloningObjectSink(inboundCloner, (ObjectSink) original);
            } else if (original instanceof LocalRequestHandlerConnector) {
                return original;
            } else if (original instanceof EndpointImpl) {
                return original;
            } else {
                return null;
            }
        }
    }

    private interface ClonerPairSource {
        Pair<ObjectCloner, ObjectCloner> createPair();

        Pair<ObjectCloner, ObjectCloner> IDENTITY_PAIR = Pair.create(ObjectCloner.IDENTITY, ObjectCloner.IDENTITY);

        ClonerPairSource IDENTITY = new ClonerPairSource() {
            public Pair<ObjectCloner, ObjectCloner> createPair() {
                return IDENTITY_PAIR;
            }
        };
    }

    private static class HandlerClonerSource implements ClonerPairSource {

        private final ObjectClonerFactory clonerFactory;
        private final ClassLoader requestClassLoader;
        private final ClassLoader replyClassLoader;
        private final ClonerConfiguration configuration;

        public HandlerClonerSource(final ClonerConfiguration configuration, final ObjectClonerFactory clonerFactory, final ClassLoader requestClassLoader, final ClassLoader replyClassLoader) {
            this.configuration = configuration;
            this.clonerFactory = clonerFactory;
            this.requestClassLoader = requestClassLoader;
            this.replyClassLoader = replyClassLoader;
        }

        public Pair<ObjectCloner, ObjectCloner> createPair() {
            final ClonerConfiguration requestConfiguration = configuration.clone();
            final ClonerConfiguration replyConfiguration = configuration.clone();
            final ClassLoader requestClassLoader = this.requestClassLoader;
            final ClassLoader replyClassLoader = this.replyClassLoader;
            if (requestClassLoader == replyClassLoader) {
                requestConfiguration.setClassCloner(ClassCloner.IDENTITY);
                replyConfiguration.setClassCloner(ClassCloner.IDENTITY);
            } else {
                requestConfiguration.setClassCloner(new ClassLoaderClassCloner(requestClassLoader));
                replyConfiguration.setClassCloner(new ClassLoaderClassCloner(replyClassLoader));
            }
            final LocalCloneTable requestCloneTable = new LocalCloneTable();
            final LocalCloneTable replyCloneTable = new LocalCloneTable();
            requestConfiguration.setCloneTable(requestCloneTable);
            replyConfiguration.setCloneTable(replyCloneTable);
            final ObjectCloner requestCloner = clonerFactory.createCloner(requestConfiguration);
            final ObjectCloner replyCloner = clonerFactory.createCloner(replyConfiguration);
            requestCloneTable.setInboundCloner(replyCloner);
            replyCloneTable.setInboundCloner(requestCloner);
            return Pair.create(requestCloner, replyCloner);
        }

        public ObjectCloner createNew() {
            final ClonerConfiguration configuration = this.configuration.clone();
            configuration.setCloneTable(new LocalCloneTable());
            return clonerFactory.createCloner(configuration);
        }
    }

    private static class CloningObjectSink implements ObjectSink<Object> {

        private volatile Pair<ObjectCloner, ObjectSink> pair;

        private static final AtomicReferenceFieldUpdater<CloningObjectSink, Pair> pairUpdater = AtomicReferenceFieldUpdater.newUpdater(CloningObjectSink.class, Pair.class, "pair");

        public CloningObjectSink(final ObjectCloner cloner, final ObjectSink objectSink) {
            pair = Pair.create(cloner, objectSink);
        }

        @SuppressWarnings({ "unchecked" })
        public void accept(final Object instance) throws IOException {
            final Pair<ObjectCloner, ObjectSink> pair = this.pair;
            if (pair == null) {
                throw closed();
            }
            final ObjectSink objectSink = pair.getB();
            final ObjectCloner cloner = pair.getA();
            try {
                objectSink.accept(cloner.clone(instance));
            } catch (ClassNotFoundException e) {
                throw new InvalidClassException(e.getMessage());
            }
        }

        public void flush() throws IOException {
            final Pair<ObjectCloner, ObjectSink> pair = this.pair;
            if (pair == null) {
                throw closed();
            }
            final ObjectSink objectSink = pair.getB();
            if (objectSink == null) {
                throw closed();
            }
            objectSink.flush();
        }

        private static IOException closed() {
            return new IOException("Object sink has been closed");
        }

        @SuppressWarnings({ "unchecked" })
        public void close() throws IOException {
            final Pair<ObjectCloner, ObjectSink> pair = pairUpdater.getAndSet(this, null);
            if (pair == null) {
                return;
            }
            pair.getB().close();
        }
    }

    private static class CloningObjectSource implements ObjectSource {

        private volatile Pair<ObjectSource, ObjectCloner> pair;

        private static final AtomicReferenceFieldUpdater<CloningObjectSource, Pair> pairUpdater = AtomicReferenceFieldUpdater.newUpdater(CloningObjectSource.class, Pair.class, "pair");

        CloningObjectSource(final ObjectSource objectSource, final ObjectCloner outboundCloner) {
            pair = Pair.create(objectSource, outboundCloner);
        }

        public boolean hasNext() throws IOException {
            final Pair<ObjectSource, ObjectCloner> pair = this.pair;
            if (pair == null) {
                throw closed();
            }
            return pair.getA().hasNext();
        }

        public Object next() throws NoSuchElementException, IOException {
            final Pair<ObjectSource, ObjectCloner> pair = this.pair;
            if (pair == null) {
                throw closed();
            }
            try {
                return pair.getB().clone(pair.getA().next());
            } catch (ClassNotFoundException e) {
                throw new InvalidObjectException("Class not found: " + e);
            }
        }

        private static IOException closed() {
            return new IOException("Object source has been closed");
        }

        @SuppressWarnings({ "unchecked" })
        public void close() throws IOException {
            final Pair<ObjectSource, ObjectCloner> pair = pairUpdater.getAndSet(this, null);
            if (pair == null) {
                return;
            }
            pair.getA().close();
        }
    }
}
