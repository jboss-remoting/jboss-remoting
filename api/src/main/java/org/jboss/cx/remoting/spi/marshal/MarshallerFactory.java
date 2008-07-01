package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import java.nio.Buffer;
import org.jboss.xnio.BufferAllocator;

/**
 * A factory to produce marshallers.
 *
 * @param <T> the type of buffer that the marshaller uses, typically {@link java.nio.ByteBuffer} or {@link java.nio.CharBuffer}
 */
public interface MarshallerFactory<T extends Buffer> {

    /**
     * Create a marshaller instance.
     *
     * @param allocator the buffer allocator to use
     * @param resolver the object resolver to use
     * @param classLoader the classloader to use
     * @return a marshaller
     * @throws IOException if an error occurs while creating the marshaller
     */
    Marshaller<T> createMarshaller(BufferAllocator<T> allocator, ObjectResolver resolver, ClassLoader classLoader) throws IOException;
}
