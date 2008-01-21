package org.jboss.cx.remoting.core.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 *
 */
public interface ByteOutput extends Closeable, Flushable {
    void write(int b) throws IOException;

    void write(byte[] b) throws IOException;

    void write(byte[] b, int offs, int len) throws IOException;

    void commit() throws IOException;

    int getBytesWritten() throws IOException;
}
