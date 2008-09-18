package org.jboss.remoting.stream;

import java.io.IOException;

/**
 *
 */
public class ObjectSinkWrapper<T> implements ObjectSink<T> {
    private final ObjectSink<T> target;

    public ObjectSinkWrapper(final ObjectSink<T> target) {
        this.target = target;
    }

    public void accept(final T instance) throws IOException {
        target.accept(instance);
    }

    public void flush() throws IOException {
        target.flush();
    }

    public void close() throws IOException {
        target.close();
    }
}
