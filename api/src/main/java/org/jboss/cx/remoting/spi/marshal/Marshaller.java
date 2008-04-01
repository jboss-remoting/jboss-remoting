package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import org.jboss.cx.remoting.spi.DataMessageInput;
import org.jboss.cx.remoting.spi.DataMessageOutput;
import org.jboss.cx.remoting.spi.ObjectMessageInput;
import org.jboss.cx.remoting.spi.ObjectMessageOutput;

/**
 *
 */
public interface Marshaller {
    ObjectMessageOutput getMessageOutput(DataMessageOutput dataMessageOutput) throws IOException;

    ObjectMessageInput getMessageInput(DataMessageInput dataMessageInput) throws IOException;

    Marshaller createChild() throws IOException;

    Marshaller createChild(ClassLoader classLoader) throws IOException;

    void addFirstObjectResolver(ObjectResolver resolver);

    void addLastObjectResolver(ObjectResolver resolver);
}
