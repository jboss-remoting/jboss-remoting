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
import java.nio.ByteBuffer;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.NioByteOutput;
import org.jboss.remoting3.stream.ObjectSource;
import org.jboss.xnio.IoUtils;

final class OutboundObjectSourceTransmitTask implements Runnable {

    private final ObjectSource objectSource;
    private final OutboundStream outboundStream;
    private final RemoteConnectionHandler connectionHandler;

    OutboundObjectSourceTransmitTask(final ObjectSource objectSource, final OutboundStream outboundStream, final RemoteConnectionHandler connectionHandler) {
        this.objectSource = objectSource;
        this.outboundStream = outboundStream;
        this.connectionHandler = connectionHandler;
    }

    public void run() {
        final ObjectSource objectSource = this.objectSource;
        try {
            final MarshallerFactory marshallerFactory = connectionHandler.getMarshallerFactory();
            final MarshallingConfiguration configuration = connectionHandler.getMarshallingConfiguration();
            try {
                final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
                try {
                    marshaller.start(new NioByteOutput(new NioByteOutput.BufferWriter() {
                        public ByteBuffer getBuffer() {
                            return outboundStream.getBuffer();
                        }

                        public void accept(final ByteBuffer buffer, final boolean eof) throws IOException {
                            outboundStream.send(buffer);
                            if (eof) outboundStream.sendEof();
                        }

                        public void flush() throws IOException {
                        }
                    }));
                    while (objectSource.hasNext()) {
                        marshaller.writeByte(RemoteProtocol.OSOURCE_OBJECT);
                        marshaller.writeObject(objectSource.next());
                    }
                    marshaller.writeByte(RemoteProtocol.OSOURCE_CLOSE);
                    marshaller.finish();
                    marshaller.close();
                } finally {
                    IoUtils.safeClose(marshaller);
                }
            } catch (Exception e) {
                outboundStream.sendException();
            }
        } finally {
            IoUtils.safeClose(objectSource);
        }
    }
}
