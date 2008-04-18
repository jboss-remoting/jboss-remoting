package org.jboss.cx.remoting.util;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
public class InputStreamByteMessageInput implements ByteMessageInput {
    private final InputStream inputStream;
    private int remaining;

    public InputStreamByteMessageInput(final InputStream inputStream, final int size) {
        this.inputStream = inputStream;
        remaining = size;
    }

    public int read() throws IOException {
        final int data = inputStream.read();
        if (data != -1 && remaining >= 0) {
            remaining--;
        }
        return data;
    }

    public int read(final byte[] data) throws IOException {
        final int cnt = inputStream.read(data);
        if (cnt != -1 && remaining >= 0) {
            remaining -= cnt;
        }
        return cnt;
    }

    public int read(final byte[] data, final int offs, final int len) throws IOException {
        final int cnt = inputStream.read(data, offs, len);
        if (cnt != -1 && remaining >= 0) {
            remaining -= cnt;
        }
        return cnt;
    }

    public int remaining() {
        final int remaining = this.remaining;
        return remaining < 0 ? -1 : remaining;
    }

    public void close() throws IOException {
        remaining = -1;
        inputStream.close();
    }
}
