package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import org.jboss.cx.remoting.spi.marshal.Marshaller;
import org.jboss.cx.remoting.spi.marshal.MarshallerFactory;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;

/**
 *
 */
public class JBossSerializationMarshallerFactory implements MarshallerFactory {

    public Marshaller createRootMarshaller(final ObjectResolver resolver, final ClassLoader classLoader) throws IOException {
        return new JBossSerializationMarhsaller(resolver, classLoader);
    }
}
