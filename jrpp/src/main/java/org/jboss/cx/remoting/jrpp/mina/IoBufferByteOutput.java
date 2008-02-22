package org.jboss.cx.remoting.jrpp.mina;

import java.io.IOException;
import org.apache.mina.common.IoBuffer;
import org.apache.mina.common.IoSession;
import org.jboss.cx.remoting.util.ByteOutput;

/**
 *
 */
public final class IoBufferByteOutput implements ByteOutput {
    private final IoBuffer ioBuffer;
    private final IoSession ioSession;

    public IoBufferByteOutput(final IoBuffer ioBuffer, final IoSession ioSession) {
        this.ioBuffer = ioBuffer;
        this.ioSession = ioSession;
    }

    public void write(int b) throws IOException {
        ioBuffer.put((byte)b);
    }

    public void write(byte[] b) throws IOException {
        ioBuffer.put(b);
    }

    public void write(byte[] b, int offs, int len) throws IOException {
        ioBuffer.put(b, offs, len);
    }

    public void commit() throws IOException {
        final IoBuffer buffer = ioBuffer.flip().skip(4);
        ioSession.write(buffer);
    }

    public int getBytesWritten() {
        return ioBuffer.position();
    }

    public void close() throws IOException {
    }

    public void flush() throws IOException {
    }
}
