package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import java.io.Serializable;
import java.nio.Buffer;
import org.jboss.cx.remoting.stream.ObjectSink;
import org.jboss.cx.remoting.stream.ObjectSource;

/**
 * A marshaller for transmitting data over a wire protocol of some sort.  Each marshaller instance is
 * guaranteed to be used by only one thread at a time.
 *
 * @param <T> the type of buffer that the marshaller uses, typically {@link java.nio.ByteBuffer} or {@link java.nio.CharBuffer}
 */
public interface Marshaller<T extends Buffer> {

    void start(Object object) throws IOException, IllegalStateException;

    boolean marshal(T buffer) throws IOException;

    void clearClassPool() throws IOException;
}
