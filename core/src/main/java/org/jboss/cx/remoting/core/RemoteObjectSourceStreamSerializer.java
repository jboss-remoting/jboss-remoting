package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.stream.ObjectSource;

/**
 *
 */
public final class RemoteObjectSourceStreamSerializer implements ObjectSource<ByteBuffer> {
    public RemoteObjectSourceStreamSerializer(final StreamContext context) {

    }

    public boolean hasNext() throws IOException {
        return false;
    }

    public ByteBuffer next() throws IOException {
        return null;
    }

    public void close() throws IOException {
    }
}
