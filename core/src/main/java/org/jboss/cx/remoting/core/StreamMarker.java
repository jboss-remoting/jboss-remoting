package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import java.io.Externalizable;
import java.io.ObjectOutput;
import java.io.IOException;
import java.io.ObjectInput;

/**
 *
 */
public final class StreamMarker implements Externalizable {

    private Class<? extends StreamSerializerFactory> factoryClass;
    private StreamIdentifier streamIdentifier;
    private CoreSession coreSession;

    public StreamMarker(final CoreSession coreSession, final Class<? extends StreamSerializerFactory> factoryClass, final StreamIdentifier streamIdentifier) {
        this.coreSession = coreSession;
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
        coreSession.getProtocolHandler().writeStreamIdentifier(out, streamIdentifier);
    }

    @SuppressWarnings ({"unchecked"})
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        coreSession = CoreSession.getInstance();
        factoryClass = (Class<? extends StreamSerializerFactory>) in.readObject();
        coreSession.getProtocolHandler().readStreamIdentifier(in);
    }
}
