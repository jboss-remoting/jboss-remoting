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
