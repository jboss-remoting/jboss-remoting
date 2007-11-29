package org.jboss.cx.remoting.spi.filter;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.stream.ObjectSink;
import org.jboss.cx.remoting.stream.ObjectSource;

/**
 * A protocol filter factory.  This object produces filters that can be applied to a protocol stream.  Filters can
 * provide services such as encryption and compression.  One or more instances of the same filter may be associated with
 * a session, particularly if there are multiple connections involved.
 */
public interface FilterFactory {

    /**
     * Create a new filter instance.
     *
     * @param input the source of input data
     * @param output the sink of output data
     * @param session the session with which this filter is associated
     *
     * @return a new filter
     *
     * @throws IOException if an error occurs
     */
    Filter newFilter(ObjectSource<ByteBuffer> input, ObjectSink<ByteBuffer> output, Session session) throws IOException;
}
