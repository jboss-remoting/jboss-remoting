package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;

/**
 *
 */
public interface MarshallerFactory {
    Marshaller createRootMarshaller(ObjectResolver resolver, ClassLoader classLoader) throws IOException;
}
