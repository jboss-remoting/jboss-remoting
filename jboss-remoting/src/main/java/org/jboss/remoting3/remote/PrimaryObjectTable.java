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
import java.io.OutputStream;
import java.io.Reader;
import java.io.StreamCorruptedException;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.NioByteInput;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.util.IntKeyMap;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.stream.ObjectSink;
import org.jboss.remoting3.stream.ObjectSource;
import org.jboss.remoting3.stream.ReaderInputStream;
import org.jboss.remoting3.stream.WriterOutputStream;
import org.jboss.xnio.log.Logger;

final class PrimaryObjectTable implements ObjectTable {

    private final Endpoint endpoint;
    private final RemoteConnectionHandler connectionHandler;
    private final Executor executor;
    private static final Logger log = Loggers.main;

    PrimaryObjectTable(final Endpoint endpoint, final RemoteConnectionHandler connectionHandler) {
        this.endpoint = endpoint;
        this.connectionHandler = connectionHandler;
        executor = this.connectionHandler.getConnectionContext().getConnectionProviderContext().getExecutor();
    }

    private static final Writer ZERO_WRITER = new ByteWriter(RemoteProtocol.OBJ_ENDPOINT);
    private static final Writer ONE_WRITER = new ByteWriter(RemoteProtocol.OBJ_CLIENT_CONNECTOR);

    private static final class ByteWriter implements Writer {
        private final byte b;

        private ByteWriter(final int b) {
            this.b = (byte) b;
        }

        public void writeObject(final Marshaller marshaller, final Object object) throws IOException {
            marshaller.writeByte(b);
        }
    }

    public Writer getObjectWriter(final Object object) throws IOException {
        if (object == endpoint) {
            return ZERO_WRITER;
        } else if (object == PrimaryExternalizerFactory.RequestHandlerConnectorExternalizer.INSTANCE) {
            return ONE_WRITER;
        } else if (object instanceof InputStream) {
            return new Writer() {
                public void writeObject(final Marshaller marshaller, final Object object) throws IOException {
                    writeOutboundStream(marshaller, RemoteProtocol.OBJ_INPUT_STREAM, (InputStream) object);
                }
            };
        } else if (object instanceof OutputStream) {
            return new Writer() {
                public void writeObject(final Marshaller marshaller, final Object object) throws IOException {
                    writeInboundStream(marshaller, RemoteProtocol.OBJ_OUTPUT_STREAM, (OutputStream) object);
                }
            };
        } else if (object instanceof Reader) {
            return new Writer() {
                public void writeObject(final Marshaller marshaller, final Object object) throws IOException {
                    writeOutboundStream(marshaller, RemoteProtocol.OBJ_READER, new ReaderInputStream((Reader)object, RemoteProtocol.UTF_8));
                }
            };
        } else if (object instanceof java.io.Writer) {
            return new Writer() {
                public void writeObject(final Marshaller marshaller, final Object object) throws IOException {
                    writeInboundStream(marshaller, RemoteProtocol.OBJ_WRITER, new WriterOutputStream((java.io.Writer)object, RemoteProtocol.UTF_8));
                }
            };
        } else if (object instanceof ObjectSource) {
            return new Writer() {
                public void writeObject(final Marshaller marshaller, final Object object) throws IOException {
                    writeOutboundStream(marshaller, RemoteProtocol.OBJ_OBJECT_SOURCE, (ObjectSource) object);
                }
            };
        } else if (object instanceof ObjectSink) {
            return new Writer() {
                public void writeObject(final Marshaller marshaller, final Object object) throws IOException {
                    writeInboundStream(marshaller, RemoteProtocol.OBJ_OBJECT_SINK, (ObjectSink) object);
                }
            };
        } else {
            return null;
        }
    }

    private void writeInboundStream(final Marshaller marshaller, final byte code, final ObjectSink objectSink) throws IOException {
        marshaller.writeByte(code);
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

    private void writeOutboundStream(final Marshaller marshaller, final byte code, final ObjectSource objectSource) throws IOException {
        marshaller.writeByte(code);
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

    /**
     * This looks backwards but it really isn't.  When we write an OutputStream, we want the remote side to send us inbound
     * to feed it.
     *
     * @param marshaller the marshaller
     * @param code the code
     * @param outputStream the output stream
     * @throws IOException if an I/O error occurs
     */
    private void writeInboundStream(final Marshaller marshaller, final byte code, final OutputStream outputStream) throws IOException {
        marshaller.writeByte(code);
        final IntKeyMap<InboundStream> inboundStreams = connectionHandler.getInboundStreams();
        final Random random = connectionHandler.getRandom();
        int id;
        synchronized (inboundStreams) {
            while (inboundStreams.containsKey(id = random.nextInt() & ~1));
            inboundStreams.put(id, new InboundStream(id, connectionHandler.getRemoteConnection(), outputStream));
        }
        marshaller.writeInt(id);
    }

    private void writeOutboundStream(final Marshaller marshaller, final byte code, final InputStream inputStream) throws IOException {
        marshaller.writeByte(code);
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

    public Object readObject(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
        final int id = unmarshaller.readUnsignedByte();
        switch (id) {
            case RemoteProtocol.OBJ_ENDPOINT: return endpoint;
            case RemoteProtocol.OBJ_CLIENT_CONNECTOR: return PrimaryExternalizerFactory.RequestHandlerConnectorExternalizer.INSTANCE;
            default: throw new StreamCorruptedException("Unknown object table ID byte " + id);
        }
    }
}
