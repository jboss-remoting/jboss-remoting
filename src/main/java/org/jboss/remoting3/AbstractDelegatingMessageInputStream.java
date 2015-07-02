/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3;

import java.io.IOException;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractDelegatingMessageInputStream extends MessageInputStream {
    private final MessageInputStream delegate;

    protected AbstractDelegatingMessageInputStream(final MessageInputStream delegate) {
        this.delegate = delegate;
    }

    protected MessageInputStream getDelegate() {
        return delegate;
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

    public String readLine() throws UnsupportedOperationException {
        return delegate.readLine();
    }

    public String readUTF() throws IOException {
        return delegate.readUTF();
    }

    public int read() throws IOException {
        return delegate.read();
    }

    public int read(final byte[] b) throws IOException {
        return delegate.read(b);
    }

    public int read(final byte[] b, final int off, final int len) throws IOException {
        return delegate.read(b, off, len);
    }

    public long skip(final long n) throws IOException {
        return delegate.skip(n);
    }

    public int available() throws IOException {
        return delegate.available();
    }

    public void close() throws IOException {
        delegate.close();
    }

    public void mark(final int readlimit) {
        delegate.mark(readlimit);
    }

    public void reset() throws IOException {
        delegate.reset();
    }

    public boolean markSupported() {
        return delegate.markSupported();
    }
}
