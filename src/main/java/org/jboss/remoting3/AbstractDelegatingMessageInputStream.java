/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
