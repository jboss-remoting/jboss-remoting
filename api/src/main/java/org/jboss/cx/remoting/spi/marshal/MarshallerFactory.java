package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;

/**
 *
 */
public interface MarshallerFactory {
    Marshaller createRootMarshaller(ClassLoader classLoader) throws IOException;
}
