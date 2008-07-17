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
import java.io.ObjectInputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.spi.marshal.Unmarshaller;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public abstract class AbstractSerializationUnmarshaller implements Unmarshaller<ByteBuffer> {
    private static final Logger log = Logger.getLogger(AbstractSerializationUnmarshaller.class);

    private final Executor executor;
    private final ObjectInputStream objectInputStream;
    private final Object resultLock = new Object();

    protected final ObjectResolver resolver;
    protected final OneBufferInputStream inputStream = new OneBufferInputStream(resultLock);

    private boolean done = true;
    private Object result;
    private Throwable cause;

    protected AbstractSerializationUnmarshaller(final Executor executor, final ObjectResolver resolver) throws IOException {
        this.executor = executor;
        this.resolver = resolver;
        objectInputStream = getObjectInputStream();
    }

    protected abstract ObjectInputStream getObjectInputStream() throws IOException;

    public boolean unmarshal(final ByteBuffer buffer) throws IOException {
        synchronized (resultLock) {
            if (done) {
                done = false;
                executor.execute(new Runnable() {
                    public void run() {
                        synchronized (resultLock) {
                            try {
                                result = objectInputStream.readObject();
                                log.trace("Successfully unmarshalled object %s", result);
                            } catch (Throwable t) {
                                cause = t;
                                log.trace(t, "Failed to unmarshal an object");
                            }
                            done = true;
                        }
                    }
                });
            }
            inputStream.setBuffer(buffer, false);
            try {
                while (! inputStream.isWaiting() && ! done) {
                    resultLock.wait();
                }
                return done;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("unmarshal operation was interrupted");
            }
        }
    }

    public Object get() throws IOException, IllegalStateException, ClassNotFoundException {
        synchronized (resultLock) {
            while (! done) {
                try {
                    resultLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted while waiting for marshaling result");
                }
            }
            if (cause != null) {
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                } else if (cause instanceof ClassNotFoundException) {
                    throw (ClassNotFoundException) cause;
                } else {
                    throw new RuntimeException("Unmarshalling failed unexpectedly", cause);
                }
            }
            return result;
        }
    }
}
