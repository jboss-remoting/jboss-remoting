package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import java.io.ObjectStreamClass;
import java.nio.ByteBuffer;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.HashMap;
import org.jboss.cx.remoting.spi.marshal.Marshaller;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.cx.remoting.stream.ObjectSink;
import org.jboss.cx.remoting.stream.ObjectSource;
import org.jboss.cx.remoting.stream.ByteBufferOutputStream;
import org.jboss.cx.remoting.stream.ByteBufferInputStream;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.xnio.BufferAllocator;
import org.jboss.serial.io.JBossObjectOutputStream;
import org.jboss.serial.io.JBossObjectInputStream;

/**
 *
 */
public class JBossSerializationMarhsaller implements Marshaller<ByteBuffer> {

    private static final long serialVersionUID = -8197192536466706414L;

    private final BufferAllocator<ByteBuffer> allocator;
    private final ObjectResolver resolver;
    private final ClassLoader classLoader;

    public JBossSerializationMarhsaller(final BufferAllocator<ByteBuffer> allocator, final ObjectResolver resolver, final ClassLoader classLoader) {
        this.allocator = allocator;
        this.resolver = resolver;
        this.classLoader = classLoader;
    }

    public ObjectSink<Object> getMarshalingSink(final ObjectSink<ByteBuffer> bufferSink) throws IOException {
        return new MarshalingSink(bufferSink, allocator, resolver);
    }

    public ObjectSource<Object> getUnmarshalingSource(final ObjectSource<ByteBuffer> bufferSource) throws IOException {
        return new MarshalingSource(bufferSource, allocator, resolver, classLoader);
    }

    public static final class MarshalingSink implements ObjectSink<Object> {
        private final OurObjectOutputStream stream;

        private MarshalingSink(final ObjectSink<ByteBuffer> bufferSink, final BufferAllocator<ByteBuffer> allocator, final ObjectResolver resolver) throws IOException {
            stream = new OurObjectOutputStream(bufferSink, allocator, resolver);
        }

        public void accept(final Object instance) throws IOException {
            stream.writeObject(instance);
        }

        public void flush() throws IOException {
            stream.flush();
        }

        public void close() throws IOException {
            stream.close();
        }
    }

    private static final class OurObjectOutputStream extends JBossObjectOutputStream {
        private final ObjectResolver resolver;

        private OurObjectOutputStream(final ObjectSink<ByteBuffer> sink, final BufferAllocator<ByteBuffer> allocator, final ObjectResolver resolver) throws IOException {
            super(new ByteBufferOutputStream(sink, allocator));
            enableReplaceObject(true);
            this.resolver = resolver;
        }

        protected Object replaceObject(final Object obj) throws IOException {
            return resolver.writeReplace(obj);
        }
    }

    public static final class MarshalingSource implements ObjectSource<Object> {
        private final OurObjectInputStream stream;

        private MarshalingSource(final ObjectSource<ByteBuffer> bufferSource, final BufferAllocator<ByteBuffer> allocator, final ObjectResolver resolver, final ClassLoader classLoader) throws IOException {
            stream = new OurObjectInputStream(bufferSource, allocator, resolver, classLoader);
        }

        public boolean hasNext() throws IOException {
            return true;
        }

        public Object next() throws IOException {
            try {
                return stream.readObject();
            } catch (ClassNotFoundException e) {
                throw new RemotingException("No class found for next object in stream", e);
            }
        }

        public void close() throws IOException {
            stream.close();
        }
    }

    private static final class OurObjectInputStream extends JBossObjectInputStream {
        private final ClassLoader classLoader;
        private final ObjectResolver resolver;

        private OurObjectInputStream(final ObjectSource<ByteBuffer> bufferSource, final BufferAllocator<ByteBuffer> allocator, final ObjectResolver resolver, final ClassLoader classLoader) throws IOException {
            super(new ByteBufferInputStream(bufferSource, allocator), classLoader);
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
