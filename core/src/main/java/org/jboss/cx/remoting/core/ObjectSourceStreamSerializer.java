package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.stream.ObjectSource;
import java.nio.ByteBuffer;
import java.io.IOException;

/**
 *
 */
public final class ObjectSourceStreamSerializer implements StreamSerializer {
    private final StreamContext streamContext;
    private final ObjectSource<?> objectSource;

    private static enum Command {
    }

    public ObjectSourceStreamSerializer(final StreamContext streamContext, final ObjectSource<?> objectSource) {
        this.streamContext = streamContext;
        this.objectSource = objectSource;
    }

    public void handleData(Object object) throws IOException {
        streamContext.sendData(objectSource.hasNext() ? objectSource.next() : null);
    }
}
