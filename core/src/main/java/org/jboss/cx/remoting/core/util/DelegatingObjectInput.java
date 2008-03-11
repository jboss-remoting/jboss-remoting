package org.jboss.cx.remoting.core.util;

import java.io.IOException;
import java.io.ObjectInput;

/**
 *
 */
public class DelegatingObjectInput implements ObjectInput {
    private final ObjectInput delegate;

    public DelegatingObjectInput(final ObjectInput delegate) {
        this.delegate = delegate;
    }

    public int read() throws IOException {
        return delegate.read();
    }

    public int read(final byte[] data) throws IOException {
        return delegate.read(data);
    }

    public int read(final byte[] data, final int offs, final int len) throws IOException {
        return delegate.read(data, offs, len);
    }

    public void close() throws IOException {
        delegate.close();
    }

    public Object readObject() throws ClassNotFoundException, IOException {
        return delegate.readObject();
    }

    public long skip(final long n) throws IOException {
        return delegate.skip(n);
    }

    public int available() throws IOException {
        return delegate.available();
    }

    public void readFully(final byte[] b) throws IOException {
        delegate.readFully(b);
    }

    public void readFully(final byte[] b, final int off, final int len) throws IOException {
        delegate.readFully(b, off, len);
    }

    public int skipBytes(final int n) throws IOException {
        return delegate.skipBytes(n);
    }

    public boolean readBoolean() throws IOException {
        return delegate.readBoolean();
    }

    public byte readByte() throws IOException {
        return delegate.readByte();
    }

    public int readUnsignedByte() throws IOException {
        return delegate.readUnsignedByte();
    }

    public short readShort() throws IOException {
        return delegate.readShort();
    }

    public int readUnsignedShort() throws IOException {
        return delegate.readUnsignedShort();
    }

    public char readChar() throws IOException {
        return delegate.readChar();
    }

    public int readInt() throws IOException {
        return delegate.readInt();
    }

    public long readLong() throws IOException {
        return delegate.readLong();
    }

    public float readFloat() throws IOException {
        return delegate.readFloat();
    }

    public double readDouble() throws IOException {
        return delegate.readDouble();
    }

    public String readLine() throws IOException {
        return delegate.readLine();
    }

    public String readUTF() throws IOException {
        return delegate.readUTF();
    }
}
