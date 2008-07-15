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

package org.jboss.cx.remoting.core.marshal;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.spi.marshal.Marshaller;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public abstract class AbstractSerializationMarshaller implements Marshaller<ByteBuffer> {
    private static final Logger log = Logger.getLogger(AbstractSerializationMarshaller.class);

    private final Executor executor;
    private final ObjectOutputStream objectOutputStream;
    private final Object resultLock = new Object();

    protected final ObjectResolver resolver;
    protected final OneBufferOutputStream outputStream = new OneBufferOutputStream(resultLock);

    private boolean done = false;

    protected AbstractSerializationMarshaller(final Executor executor, final ObjectResolver resolver) throws IOException {
        this.executor = executor;
        this.resolver = resolver;
        objectOutputStream = getObjectOutputStream();
    }

    protected abstract ObjectOutputStream getObjectOutputStream() throws IOException;

    public void start(final Object object) throws IOException, IllegalStateException {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    log.trace("Beginning serializing object %s", object);
                    synchronized (objectOutputStream) {
                        objectOutputStream.writeObject(object);
                        log.trace("Flushing stream");
                        objectOutputStream.flush();
                        synchronized (resultLock) {
                            outputStream.flush();
                            done = true;
                            resultLock.notify();
                            log.trace("Completed serializing object %s", object);
                        }
                    }
                } catch (Throwable t) {
                    log.error(t, "Serialization error");
                }
            }
        });
    }

    public boolean marshal(final ByteBuffer buffer) throws IOException {
        log.trace("Marshalling to buffer %s", buffer);
        outputStream.setBuffer(buffer);
        synchronized (resultLock) {
            outputStream.await();
            return done;
        }
    }

    public void clearClassPool() throws IOException {
        synchronized (objectOutputStream) {
            objectOutputStream.reset();
        }
    }
}
