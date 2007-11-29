package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.stream.StreamDetector;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.stream.ObjectSource;
import org.jboss.cx.remoting.stream.ObjectSink;
import org.jboss.cx.remoting.stream.ProgressStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

/**
 *
 */
public final class DefaultStreamDetector implements StreamDetector {
    public <T> Class<? extends StreamSerializerFactory> detectStream(T candidate) {
        if (candidate instanceof InputStream) {
            return InputStreamSerializerFactory.class;
        } else if (candidate instanceof OutputStream) {

        } else if (candidate instanceof ObjectSource) {

        } else if (candidate instanceof ObjectSink) {

        } else if (candidate instanceof ProgressStream) {

        } else if (candidate instanceof Iterator) {

        } else {
            return null;
        }
        return null;
    }
}
