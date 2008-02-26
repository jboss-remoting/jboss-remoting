package org.jboss.cx.remoting.service;

import java.io.Serializable;
import java.io.InputStream;

/**
 *
 */
public final class RemoteResource implements Serializable {
    private static final long serialVersionUID = 1L;

    private InputStream inputStream;
    private int size;

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(final InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public int getSize() {
        return size;
    }

    public void setSize(final int size) {
        this.size = size;
    }
}
