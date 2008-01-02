package org.jboss.cx.remoting.spi.protocol;

import java.io.Closeable;
import java.io.IOException;

/**
 *
 */
public interface ByteInput extends Closeable {
    int read() throws IOException;

    int read(byte[] data) throws IOException;

    int read(byte[] data, int offs, int len) throws IOException;

    int remaining();
}
