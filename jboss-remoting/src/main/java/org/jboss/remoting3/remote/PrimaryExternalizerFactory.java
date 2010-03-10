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

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.marshalling.AbstractExternalizer;
import org.jboss.marshalling.ClassExternalizerFactory;
import org.jboss.marshalling.Creator;
import org.jboss.marshalling.Externalizer;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.NioByteInput;
import org.jboss.marshalling.NioByteOutput;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.util.IntKeyMap;
import org.jboss.remoting3.stream.ObjectSink;
import org.jboss.remoting3.stream.ObjectSource;
import org.jboss.remoting3.stream.ReaderInputStream;
import org.jboss.remoting3.stream.WriterOutputStream;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

final class PrimaryExternalizerFactory implements ClassExternalizerFactory {

    private static final Logger log = Loggers.main;

    private final RemoteConnectionHandler connectionHandler;
    private final Executor executor;

    final Externalizer inputStream = new InputStreamExternalizer();
    final Externalizer outputStream = new OutputStreamExternalizer();
    final Externalizer reader = new ReaderExternalizer();
    final Externalizer writer = new WriterExternalizer();
    final Externalizer objectSource = new ObjectSourceExternalizer();
    final Externalizer objectSink = new ObjectSinkExternalizer();

    PrimaryExternalizerFactory(final RemoteConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
        executor = connectionHandler.getConnectionContext().getConnectionProviderContext().getExecutor();
    }

    public Externalizer getExternalizer(final Class<?> type) {
        if (type == UnsentRequestHandlerConnector.class) {
            return RequestHandlerConnectorExternalizer.INSTANCE;
        } else if (InputStream.class.isAssignableFrom(type)) {
            return inputStream;
        } else if (OutputStream.class.isAssignableFrom(type)) {
            return outputStream;
        } else if (Reader.class.isAssignableFrom(type)) {
            return reader;
        } else if (Writer.class.isAssignableFrom(type)) {
            return writer;
        } else if (ObjectSource.class.isAssignableFrom(type)) {
            return objectSource;
        } else if (ObjectSink.class.isAssignableFrom(type)) {
            return objectSink;
        } else {
            return null;
        }
    }

    static class RequestHandlerConnectorExternalizer extends AbstractExternalizer {
        static final RequestHandlerConnectorExternalizer INSTANCE = new RequestHandlerConnectorExternalizer();

        private static final long serialVersionUID = 8137262079765758375L;

        public void writeExternal(final Object subject, final ObjectOutput output) throws IOException {
            final UnsentRequestHandlerConnector connector = (UnsentRequestHandlerConnector) subject;
            output.writeInt(connector.getClientId());
        }

        public Object createExternal(final Class<?> subjectType, final ObjectInput input, final Creator defaultCreator) throws IOException, ClassNotFoundException {
            return new ReceivedRequestHandlerConnector(RemoteConnectionHandler.getCurrent(), input.readInt());
        }
    }

    class InputStreamExternalizer extends AbstractExternalizer {

        public void writeExternal(final Object subject, final ObjectOutput output) throws IOException {
            writeOutboundStream(output, (InputStream) subject);
        }

        public InputStream createExternal(final Class<?> subjectType, final ObjectInput input, final Creator defaultCreator) throws IOException, ClassNotFoundException {
            return readInboundStream(input.readInt());
        }
    }

    class OutputStreamExternalizer extends AbstractExternalizer {

        public void writeExternal(final Object subject, final ObjectOutput output) throws IOException {
            writeInboundStream(output, (OutputStream) subject);
        }

        public OutputStream createExternal(final Class<?> subjectType, final ObjectInput input, final Creator defaultCreator) throws IOException, ClassNotFoundException {
            return readOutboundStream(input.readInt());
        }
    }

    class ReaderExternalizer extends AbstractExternalizer {

        public void writeExternal(final Object subject, final ObjectOutput output) throws IOException {
            writeOutboundStream(output, new ReaderInputStream((Reader)subject, RemoteProtocol.UTF_8));
        }

        public Reader createExternal(final Class<?> subjectType, final ObjectInput input, final Creator defaultCreator) throws IOException, ClassNotFoundException {
            return new InputStreamReader(readInboundStream(input.readInt()), RemoteProtocol.UTF_8);
        }
    }

    class WriterExternalizer extends AbstractExternalizer {

        public void writeExternal(final Object subject, final ObjectOutput output) throws IOException {
            writeInboundStream(output, new WriterOutputStream((java.io.Writer)subject, RemoteProtocol.UTF_8));
        }

        public java.io.Writer createExternal(final Class<?> subjectType, final ObjectInput input, final Creator defaultCreator) throws IOException, ClassNotFoundException {
            return new OutputStreamWriter(readOutboundStream(input.readInt()), RemoteProtocol.UTF_8);
        }
    }

    class ObjectSourceExternalizer extends AbstractExternalizer {

        public void writeExternal(final Object subject, final ObjectOutput output) throws IOException {
            writeOutboundStream(output, (ObjectSource) subject);
        }

        public ObjectSource createExternal(final Class<?> subjectType, final ObjectInput input, final Creator defaultCreator) throws IOException, ClassNotFoundException {
            boolean ok = false;
            final Unmarshaller unmarshaller = connectionHandler.getMarshallerFactory().createUnmarshaller(connectionHandler.getMarshallingConfiguration());
            try {
                unmarshaller.start(readInboundStream(input.readInt()));
                return new UnmarshallerObjectSource(unmarshaller);
            } finally {
                if (! ok) {
                    IoUtils.safeClose(unmarshaller);
                }
            }
        }
    }

    class ObjectSinkExternalizer extends AbstractExternalizer {

        public void writeExternal(final Object subject, final ObjectOutput output) throws IOException {
            writeInboundStream(output, (ObjectSink) subject);
        }

        public ObjectSink createExternal(final Class<?> subjectType, final ObjectInput input, final Creator defaultCreator) throws IOException, ClassNotFoundException {
            boolean ok = false;
            final Marshaller marshaller = connectionHandler.getMarshallerFactory().createMarshaller(connectionHandler.getMarshallingConfiguration());
            try {
                marshaller.start(readOutboundStream(input.readInt()));
                return new MarshallerObjectSink(marshaller);
            } finally {
                if (! ok) {
                    IoUtils.safeClose(marshaller);
                }
            }
        }
    }

    private void writeInboundStream(final ObjectOutput marshaller, final ObjectSink objectSink) throws IOException {
        final IntKeyMap<InboundStream> inboundStreams = connectionHandler.getInboundStreams();
        final Random random = connectionHandler.getRandom();
        int id;
        synchronized (inboundStreams) {
            while (inboundStreams.containsKey(id = random.nextInt() & ~1));
            inboundStreams.put(id, new InboundStream(id, connectionHandler.getRemoteConnection(), new InboundStream.ByteInputResult() {
                public void accept(final NioByteInput nioByteInput, final InboundStream inboundStream) {
                    try {
                        executor.execute(new InboundObjectSinkReceiveTask(nioByteInput, inboundStream, connectionHandler, objectSink));
                    } catch (RejectedExecutionException e) {
                        log.warn("Unable to start task for forwarded stream: %s", e);
                        inboundStream.sendAsyncException();
                    }
                }
            }));
        }
        marshaller.writeInt(id);
    }

    private NioByteInput readInboundStream(final int id) throws InvalidObjectException {
        final IntKeyMap<InboundStream> inboundStreams = connectionHandler.getInboundStreams();
        final AtomicReference<NioByteInput> ref = new AtomicReference<NioByteInput>();
        final InboundStream inboundStream;
        synchronized (inboundStreams) {
            if (inboundStreams.containsKey(id)) {
                throw duplicateId(id);
            }
            inboundStream = new InboundStream(id, connectionHandler.getRemoteConnection(), new InboundStream.ByteInputResult() {
                public void accept(final NioByteInput nioByteInput, final InboundStream inboundStream) {
                    ref.set(nioByteInput);
                }
            });
            inboundStreams.put(id, inboundStream);
        }
        synchronized (inboundStream) {
            inboundStream.sendAsyncStart();
        }
        return ref.get();
    }

    private void writeOutboundStream(final ObjectOutput marshaller, final ObjectSource objectSource) throws IOException {
        final IntKeyMap<OutboundStream> outboundStreams = connectionHandler.getOutboundStreams();
        final Random random = connectionHandler.getRandom();
        int id;
        final OutboundStream outboundStream;
        synchronized (outboundStreams) {
            while (outboundStreams.containsKey(id = random.nextInt() | 1));
            outboundStreams.put(id, outboundStream = new OutboundStream(id, connectionHandler.getRemoteConnection()));
        }
        marshaller.writeInt(id);
        try {
            executor.execute(new OutboundObjectSourceTransmitTask(objectSource, outboundStream, connectionHandler));
        } catch (RejectedExecutionException e) {
            log.warn("Unable to start task for forwarded stream: %s", e);
            outboundStream.sendException();
        }
    }

    private NioByteOutput readOutboundStream(final int id) throws InvalidObjectException {
        final IntKeyMap<OutboundStream> outboundStreams = connectionHandler.getOutboundStreams();
        final OutboundStream outboundStream;
        synchronized (outboundStreams) {
            if (outboundStreams.containsKey(id)) {
                throw duplicateId(id);
            }
            outboundStream = new OutboundStream(id, connectionHandler.getRemoteConnection());
            outboundStreams.put(id, outboundStream);
        }
        synchronized (outboundStream) {
            outboundStream.asyncStart();
        }
        return new NioByteOutput(new NioByteOutput.BufferWriter() {
            public ByteBuffer getBuffer() {
                return outboundStream.getBuffer();
            }

            public void accept(final ByteBuffer buffer, final boolean eof) throws IOException {
                outboundStream.send(buffer);
                if (eof) {
                    outboundStream.sendEof();
                }
            }

            public void flush() throws IOException {
            }
        });
    }

    /**
     * This looks backwards but it really isn't.  When we write an OutputStream, we want the remote side to send us inbound
     * to feed it.
     *
     * @param marshaller the marshaller
     * @param outputStream the output stream
     * @throws IOException if an I/O error occurs
     */
    private void writeInboundStream(final ObjectOutput marshaller, final OutputStream outputStream) throws IOException {
        final IntKeyMap<InboundStream> inboundStreams = connectionHandler.getInboundStreams();
        final Random random = connectionHandler.getRandom();
        int id;
        synchronized (inboundStreams) {
            while (inboundStreams.containsKey(id = random.nextInt() & ~1));
            inboundStreams.put(id, new InboundStream(id, connectionHandler.getRemoteConnection(), outputStream));
        }
        marshaller.writeInt(id);
    }

    private void writeOutboundStream(final ObjectOutput marshaller, final InputStream inputStream) throws IOException {
        final IntKeyMap<OutboundStream> outboundStreams = connectionHandler.getOutboundStreams();
        final Random random = connectionHandler.getRandom();
        int id;
        final OutboundStream outboundStream;
        synchronized (outboundStreams) {
            while (outboundStreams.containsKey(id = random.nextInt() | 1));
            outboundStreams.put(id, outboundStream = new OutboundStream(id, connectionHandler.getRemoteConnection()));
        }
        marshaller.writeInt(id);
        try {
            executor.execute(new OutboundInputStreamTransmitTask(inputStream, outboundStream));
        } catch (RejectedExecutionException e) {
            log.warn("Unable to start task for forwarded stream: %s", e);
            outboundStream.sendException();
        }
    }

    private static InvalidObjectException duplicateId(final int id) {
        return new InvalidObjectException("Duplicated stream ID " + id);
    }
}
