package org.jboss.cx.remoting.spi.stream;

import java.io.IOException;

/**
 *
 */
public interface StreamSerializerFactory {
    StreamSerializer getLocalSide(StreamContext context, Object local) throws IOException;

    RemoteStreamSerializer getRemoteSide(StreamContext context) throws IOException;
}
