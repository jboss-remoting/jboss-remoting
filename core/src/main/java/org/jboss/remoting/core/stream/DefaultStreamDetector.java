package org.jboss.remoting.core.stream;

import java.io.InputStream;
import java.io.OutputStream;
import org.jboss.remoting.spi.stream.StreamDetector;
import org.jboss.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.remoting.stream.ObjectSink;
import org.jboss.remoting.stream.ObjectSource;

/**
 *
 */
public final class DefaultStreamDetector implements StreamDetector {
    public static final StreamDetector INSTANCE = new DefaultStreamDetector();

    public StreamSerializerFactory detectStream(Object candidate) {
        if (candidate instanceof InputStream) {
            return new InputStreamStreamSerializerFactory();
        } else if (candidate instanceof OutputStream) {
            return new OutputStreamStreamSerailizerFactory();
        } else if (candidate instanceof ObjectSource) {
            return new ObjectSourceStreamSerializerFactory();
        } else if (candidate instanceof ObjectSink) {
            return null;
        } else {
            return null;
        }
    }
}
