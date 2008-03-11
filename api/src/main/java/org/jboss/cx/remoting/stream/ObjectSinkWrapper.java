package org.jboss.cx.remoting.stream;

import java.io.IOException;

/**
 *
 */
public class ObjectSinkWrapper<T> implements ObjectSink<T> {
    private ObjectSink<T> target;

    protected ObjectSinkWrapper() {
    }

    public ObjectSinkWrapper(final ObjectSink<T> target) {
        this.target = target;
    }

    protected final ObjectSink<T> getTarget() {
        return target;
    }

    protected final void setTarget(final ObjectSink<T> target) {
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
