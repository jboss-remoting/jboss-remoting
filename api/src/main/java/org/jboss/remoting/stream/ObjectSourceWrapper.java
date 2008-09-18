package org.jboss.remoting.stream;

import java.io.IOException;

/**
 *
 */
public class ObjectSourceWrapper<T> implements ObjectSource<T> {
    private final ObjectSource<T> target;

    public ObjectSourceWrapper(final ObjectSource<T> target) {
        this.target = target;
    }

    public boolean hasNext() throws IOException {
        return target.hasNext();
    }

    public T next() throws IOException {
        return target.next();
    }

    public void close() throws IOException {
        target.close();
    }
}
