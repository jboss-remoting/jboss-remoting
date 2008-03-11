package org.jboss.cx.remoting.core;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;

/**
 *
 */
public final class StreamMarker implements Externalizable {

    private static final long serialVersionUID = 1L;

    private Class<? extends StreamSerializerFactory> factoryClass;
    private StreamIdentifier streamIdentifier;

    public StreamMarker(final Class<? extends StreamSerializerFactory> factoryClass, final StreamIdentifier streamIdentifier) {
        if (factoryClass == null) {
            throw new NullPointerException("factoryClass is null");
        }
        if (streamIdentifier == null) {
            throw new NullPointerException("streamIdentifier is null");
        }
        this.factoryClass = factoryClass;
        this.streamIdentifier = streamIdentifier;
    }

    public StreamMarker() {
    }

    public Class<? extends StreamSerializerFactory> getFactoryClass() {
        return factoryClass;
    }

    public StreamIdentifier getStreamIdentifier() {
        return streamIdentifier;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(factoryClass);
        out.writeObject(streamIdentifier);
    }

    @SuppressWarnings ({"unchecked"})
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        factoryClass = (Class<? extends StreamSerializerFactory>) in.readObject();
        streamIdentifier = (StreamIdentifier) in.readObject();
    }
}
