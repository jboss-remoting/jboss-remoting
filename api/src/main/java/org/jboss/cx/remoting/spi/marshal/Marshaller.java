package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import java.io.Serializable;
import org.jboss.cx.remoting.util.ObjectMessageInput;
import org.jboss.cx.remoting.util.ObjectMessageOutput;
import org.jboss.cx.remoting.util.ByteMessageOutput;
import org.jboss.cx.remoting.util.ByteMessageInput;

/**
 * A marshaller/unmarshaller for transmitting data over a wire protocol of some sort.  Each marshaller instance is
 * guaranteed to be used by only one thread.  Marshallers are not pooled or reused in any way.  Any pooling of marshallers
 * must be done by implementations of this class and/or {@link org.jboss.cx.remoting.spi.marshal.MarshallerFactory}.
 */
public interface Marshaller extends Serializable {

    /**
     * Get a message writer that marshals to the given stream.
     *
     * @param byteMessageOutput the target stream
     * @return the message writer
     * @throws IOException if an error occurs
     */
    ObjectMessageOutput getMessageOutput(ByteMessageOutput byteMessageOutput) throws IOException;

    /**
     * Get a message reader that unmarshals from the given stream.
     *
     * @param byteMessageInput the source stream
     * @return the message reader
     * @throws IOException if an error occurs
     */
    ObjectMessageInput getMessageInput(ByteMessageInput byteMessageInput) throws IOException;
}
