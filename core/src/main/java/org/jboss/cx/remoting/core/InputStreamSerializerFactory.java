package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.core.util.BufferFactory;
import org.jboss.cx.remoting.stream.Streams;
import java.io.InputStream;

/**
 *
 */
public final class InputStreamSerializerFactory implements StreamSerializerFactory<InputStream> {
    public InputStreamSerializerFactory() {
        // no-arg constructor required
    }

    public StreamSerializer getLocalSide(final StreamContext context, final InputStream local) {
        return new ObjectSourceStreamSerializer(context, Streams.getObjectSource(local, BufferFactory.create(256, false), true));
    }

    public InputStream getRemoteSide(final StreamContext context) {
        return Streams.getInputStream(new RemoteObjectSourceStreamSerializer(context), true);
    }
}
