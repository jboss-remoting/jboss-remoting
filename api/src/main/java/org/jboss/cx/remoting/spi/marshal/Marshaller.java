package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import java.io.Serializable;
import org.jboss.cx.remoting.util.DataMessageInput;
import org.jboss.cx.remoting.util.DataMessageOutput;
import org.jboss.cx.remoting.util.ObjectMessageInput;
import org.jboss.cx.remoting.util.ObjectMessageOutput;

/**
 *
 */
public interface Marshaller extends Serializable {
    ObjectMessageOutput getMessageOutput(DataMessageOutput dataMessageOutput) throws IOException;

    ObjectMessageInput getMessageInput(DataMessageInput dataMessageInput) throws IOException;

    void addFirstObjectResolver(ObjectResolver resolver);

    void addLastObjectResolver(ObjectResolver resolver);
}
