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
 * An abstract base class for message output streams which delegate to an underlying stream.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractDelegatingMessageOutputStream extends MessageOutputStream {
    private final MessageOutputStream delegate;

    protected AbstractDelegatingMessageOutputStream(final MessageOutputStream delegate) {
        this.delegate = delegate;
    }

    protected MessageOutputStream getDelegate() {
        return delegate;
    }

    public void flush() throws IOException {
        delegate.flush();
    }

    public void close() throws IOException {
        delegate.close();
    }

    public MessageOutputStream cancel() {
        delegate.cancel();
        return this;
    }

    public void writeBoolean(final boolean v) throws IOException {
        delegate.writeBoolean(v);
    }

    public void writeByte(final int v) throws IOException {
        delegate.writeByte(v);
    }

    public void writeShort(final int v) throws IOException {
        delegate.writeShort(v);
    }

    public void writeChar(final int v) throws IOException {
        delegate.writeChar(v);
    }

    public void writeInt(final int v) throws IOException {
        delegate.writeInt(v);
    }

    public void writeLong(final long v) throws IOException {
        delegate.writeLong(v);
    }

    public void writeFloat(final float v) throws IOException {
        delegate.writeFloat(v);
    }

    public void writeDouble(final double v) throws IOException {
        delegate.writeDouble(v);
    }

    public void writeBytes(final String s) throws IOException {
        delegate.writeBytes(s);
    }

    public void writeChars(final String s) throws IOException {
        delegate.writeChars(s);
    }

    public void writeUTF(final String s) throws IOException {
        delegate.writeUTF(s);
    }

    public void write(final int b) throws IOException {
        delegate.write(b);
    }

    public void write(final byte[] b) throws IOException {
        delegate.write(b);
    }

    public void write(final byte[] b, final int off, final int len) throws IOException {
        delegate.write(b, off, len);
    }
}
