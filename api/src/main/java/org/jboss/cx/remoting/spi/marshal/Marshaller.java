package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import java.io.Serializable;
import java.nio.Buffer;
import org.jboss.cx.remoting.stream.ObjectSink;
import org.jboss.cx.remoting.stream.ObjectSource;

/**
 * A marshaller/unmarshaller for transmitting data over a wire protocol of some sort.  Each marshaller instance is
 * guaranteed to be used by only one thread.  Marshallers are not pooled or reused in any way.  Any pooling of marshallers
 * must be done by implementations of this class and/or {@link org.jboss.cx.remoting.spi.marshal.MarshallerFactory}.
 *
 * @param <T> the type of buffer that the marshaller uses, typically {@link java.nio.ByteBuffer} or {@link java.nio.CharBuffer}
 */
public interface Marshaller<T extends Buffer> extends Serializable {

    /**
     * Write objects to buffers.  The buffers are allocated from the {@link org.jboss.xnio.BufferAllocator} that was
     * provided to the {@link org.jboss.cx.remoting.spi.marshal.MarshallerFactory}.
     *
     * @param bufferSink the sink for filled (and flipped) buffers
     * @return a sink for objects
     * @throws IOException if an error occurs while creating the marshaling sink
     */
    ObjectSink<Object> getMarshalingSink(ObjectSink<T> bufferSink) throws IOException;

    /**
     * Read objects from buffers.  The buffers are freed to the {@link org.jboss.xnio.BufferAllocator} that was
     * provided to the {@link org.jboss.cx.remoting.spi.marshal.MarshallerFactory}.
     *
     * @param bufferSource the source for filled (and flipped) buffers
     * @return a source for objects
     * @throws IOException if an error occurs while creating the unmarshaling source
     */
    ObjectSource<Object> getUnmarshalingSource(ObjectSource<T> bufferSource) throws IOException;
}
