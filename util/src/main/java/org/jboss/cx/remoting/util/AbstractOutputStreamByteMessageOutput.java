package org.jboss.cx.remoting.util;

import java.io.OutputStream;
import java.io.IOException;

/**
 *
 */
public abstract class AbstractOutputStreamByteMessageOutput implements ByteMessageOutput {
    private final OutputStream outputStream;
    private int count;

    protected AbstractOutputStreamByteMessageOutput(final OutputStream outputStream) {
        if (outputStream == null) {
            throw new NullPointerException("outputStream is null");
        }
        this.outputStream = outputStream;
    }

    public void write(final int b) throws IOException {
        outputStream.write(b);
        count ++;
    }

    public void write(final byte[] b) throws IOException {
        outputStream.write(b);
        count += b.length;
    }

    public void write(final byte[] b, final int offs, final int len) throws IOException {
        outputStream.write(b, offs, len);
        count += len;
    }

    public int getBytesWritten() throws IOException {
        return count;
    }

    public void close() throws IOException {
        outputStream.close();
    }

    public void flush() throws IOException {
        outputStream.flush();
    }
}
