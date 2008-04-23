package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.cx.remoting.util.ByteMessageInput;
import org.jboss.cx.remoting.util.ObjectMessageInput;
import org.jboss.serial.io.JBossObjectInputStream;

/**
 *
 */
public class JBossSerializationObjectMessageInput extends JBossObjectInputStream implements ObjectMessageInput {

    private final ObjectResolver resolver;
    private final ByteMessageInput dataMessageInput;

    public JBossSerializationObjectMessageInput(final ObjectResolver resolver, final ByteMessageInput dataMessageInput, final ClassLoader classLoader) throws IOException {
        super(new InputStream() {

            public int read(final byte b[]) throws IOException {
                return dataMessageInput.read(b);
            }

            public int read(final byte b[], final int off, final int len) throws IOException {
                return dataMessageInput.read(b, off, len);
            }

            public int available() throws IOException {
                return dataMessageInput.remaining();
            }

            public void close() throws IOException {
                dataMessageInput.close();
            }

            public boolean markSupported() {
                return false;
            }

            public int read() throws IOException {
                return dataMessageInput.read();
            }
        }, classLoader);
        if (resolver == null) {
            throw new NullPointerException("resolver is null");
        }
        if (dataMessageInput == null) {
            throw new NullPointerException("dataMessageInput is null");
        }
        if (classLoader == null) {
            throw new NullPointerException("classLoader is null");
        }
        enableResolveObject(true);
        this.resolver = resolver;
        this.dataMessageInput = dataMessageInput;
    }

    public int remaining() {
        return dataMessageInput.remaining();
    }

    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        final String name = desc.getName();
        ClassLoader classLoader = getClassLoader();
        if (primitiveTypes.containsKey(name)) {
            return primitiveTypes.get(name);
        } else {
            return Class.forName(name, false, classLoader);
        }
    }

    protected Class<?> resolveProxyClass(final String[] interfaceNames) throws IOException, ClassNotFoundException {
        final ClassLoader classLoader = getClassLoader();
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
