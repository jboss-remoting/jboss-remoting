package org.jboss.cx.remoting.spi.stream;

/**
 *
 */
public interface StreamSerializerFactory<T> {
    StreamSerializer getLocalSide(StreamContext context, T local);

    T getRemoteSide(StreamContext context);
}
