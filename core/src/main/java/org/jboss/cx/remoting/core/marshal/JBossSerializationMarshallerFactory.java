package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.jboss.cx.remoting.spi.marshal.Marshaller;
import org.jboss.cx.remoting.spi.marshal.MarshallerFactory;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.xnio.BufferAllocator;

/**
 *
 */
public class JBossSerializationMarshallerFactory implements MarshallerFactory<ByteBuffer> {

    public Marshaller<ByteBuffer> createMarshaller(final BufferAllocator<ByteBuffer> allocator, final ObjectResolver resolver, final ClassLoader classLoader) throws IOException {
        return new JBossSerializationMarhsaller(allocator, resolver, classLoader);
    }
}
