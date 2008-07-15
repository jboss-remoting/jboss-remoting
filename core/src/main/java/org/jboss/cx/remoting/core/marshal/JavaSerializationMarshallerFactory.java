package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.spi.marshal.Marshaller;
import org.jboss.cx.remoting.spi.marshal.MarshallerFactory;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.cx.remoting.spi.marshal.Unmarshaller;

/**
 *
 */
public class JavaSerializationMarshallerFactory implements MarshallerFactory<ByteBuffer> {

    private final Executor executor;

    public JavaSerializationMarshallerFactory(final Executor executor) {
        this.executor = executor;
    }

    public Marshaller<ByteBuffer> createMarshaller(final ObjectResolver resolver) throws IOException {
        return new JavaSerializationMarhsaller(executor, resolver);
    }

    public Unmarshaller<ByteBuffer> createUnmarshaller(final ObjectResolver resolver, final ClassLoader classLoader) throws IOException {
        return new JavaSerializationUnmarshaller(executor, resolver, classLoader);
    }
}