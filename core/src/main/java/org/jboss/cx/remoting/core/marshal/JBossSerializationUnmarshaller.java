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

import org.jboss.cx.remoting.spi.marshal.Unmarshaller;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.serial.io.JBossObjectInputStream;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public final class JBossSerializationUnmarshaller implements Unmarshaller<ByteBuffer> {
    private final Executor executor;
    private final OurObjectInputStream objectInputStream;
    private final ClassLoader classLoader;
    private final ObjectResolver resolver;
    private final AtomicBoolean running = new AtomicBoolean();
    

    public JBossSerializationUnmarshaller(final Executor executor, final ObjectResolver resolver, final ClassLoader classLoader) throws IOException {
        this.executor = executor;
        this.resolver = resolver;
        this.classLoader = classLoader;
        objectInputStream = new OurObjectInputStream(new OneBufferInputStream(), resolver, classLoader);
    }

    public boolean unmarshal(final ByteBuffer buffer) throws IOException {
        if (! running.getAndSet(true)) {
            executor.execute(new Runnable() {
                public void run() {
                    synchronized (objectInputStream) {
                        try {
                            final Object object = objectInputStream.readObject();

                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        } finally {
                            running.set(false);
                        }
                    }
                }
            });
        }
        return false;
    }

    public Object get() throws IOException, ClassNotFoundException, IllegalStateException {
        return null;
    }

    private static final class OurObjectInputStream extends JBossObjectInputStream {
        private final ClassLoader classLoader;
        private final ObjectResolver resolver;

        private OurObjectInputStream(final InputStream inputStream, final ObjectResolver resolver, final ClassLoader classLoader) throws IOException {
            super(inputStream, classLoader);
            this.classLoader = classLoader;
            this.resolver = resolver;
        }

        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            final String name = desc.getName();
            if (primitiveTypes.containsKey(name)) {
                return primitiveTypes.get(name);
            } else {
                return Class.forName(name, false, classLoader);
            }
        }

        protected Class<?> resolveProxyClass(final String[] interfaceNames) throws IOException, ClassNotFoundException {
            final int length = interfaceNames.length;
            final Class<?>[] interfaces = new Class[length];
            for (int i = 0; i < length; i ++) {
                interfaces[i] = Class.forName(interfaceNames[i], false, classLoader);
            }
            return Proxy.getProxyClass(classLoader, interfaces);
        }

        protected Object resolveObject(final Object obj) throws IOException {
            return resolver.readResolve(obj);
        }

        private static final Map<String, Class<?>> primitiveTypes = new HashMap<String, Class<?>>();

        private static <T> void add(Class<T> type) {
            primitiveTypes.put(type.getName(), type);
        }

        static {
            add(void.class);
            add(boolean.class);
            add(byte.class);
            add(short.class);
            add(int.class);
            add(long.class);
            add(float.class);
            add(double.class);
            add(char.class);
        }
    }
}
