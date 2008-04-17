package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import java.io.Serializable;
import org.jboss.cx.remoting.spi.DataMessageInput;
import org.jboss.cx.remoting.spi.DataMessageOutput;
import org.jboss.cx.remoting.spi.ObjectMessageInput;
import org.jboss.cx.remoting.spi.ObjectMessageOutput;

/**
 *
 */
public interface Marshaller extends Serializable {
    ObjectMessageOutput getMessageOutput(DataMessageOutput dataMessageOutput) throws IOException;

    ObjectMessageInput getMessageInput(DataMessageInput dataMessageInput) throws IOException;

    void addFirstObjectResolver(ObjectResolver resolver);

    void addLastObjectResolver(ObjectResolver resolver);
}
