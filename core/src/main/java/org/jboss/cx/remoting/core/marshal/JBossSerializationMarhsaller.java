package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import org.jboss.cx.remoting.spi.marshal.Marshaller;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.cx.remoting.util.ByteMessageInput;
import org.jboss.cx.remoting.util.ByteMessageOutput;
import org.jboss.cx.remoting.util.ObjectMessageInput;
import org.jboss.cx.remoting.util.ObjectMessageOutput;
import org.jboss.cx.remoting.stream.ObjectSink;
import org.jboss.cx.remoting.stream.ObjectSource;

/**
 *
 */
public class JBossSerializationMarhsaller implements Marshaller {

    private static final long serialVersionUID = -8197192536466706414L;

    private final ObjectResolver resolver;
    private final ClassLoader classLoader;

    public JBossSerializationMarhsaller(final ObjectResolver resolver, final ClassLoader classLoader) {
        this.resolver = resolver;
        this.classLoader = classLoader;
    }

    public ObjectMessageOutput getMessageOutput(final ByteMessageOutput byteMessageOutput) throws IOException {
        return new JBossSerializationObjectMessageOutput(resolver, byteMessageOutput);
    }

    public ObjectMessageInput getMessageInput(final ByteMessageInput byteMessageInput) throws IOException {
        return new JBossSerializationObjectMessageInput(resolver, byteMessageInput, classLoader);
    }

    public ObjectSink getMarshalingSink(final ObjectSink bufferSink) throws IOException {
        return null;
    }

    public ObjectSource getUnmarshalingSource(final ObjectSource bufferSource) throws IOException {
        return null;
    }
}
