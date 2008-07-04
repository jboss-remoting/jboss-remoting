/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package org.jboss.cx.remoting.stream;

import junit.framework.TestCase;
import java.util.Arrays;
import java.nio.ByteBuffer;
import org.jboss.cx.remoting.test.support.TestByteBufferAllocator;
import org.jboss.cx.remoting.test.support.LoggingHelper;

/**
 *
 */
public final class ByteBufferInputStreamTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testBasic() throws Throwable {
        final TestByteBufferAllocator allocator = new TestByteBufferAllocator(3);
        final ByteBufferInputStream stream = new ByteBufferInputStream(Streams.<ByteBuffer>getIteratorObjectSource(Arrays.<ByteBuffer>asList(
                ByteBuffer.wrap(new byte[] { 5, 100, 30, 12, -60, 25 }),
                ByteBuffer.wrap(new byte[] { 15 }),
                ByteBuffer.wrap(new byte[] { }),
                ByteBuffer.wrap(new byte[] { 100, 0, 0, -128, 127, 0 })).iterator()), allocator);
        assertEquals(5, stream.read());
        assertEquals(100, stream.read());
        assertEquals(30, stream.read());
        assertEquals(12, stream.read());
        assertEquals(-60 & 0xff, stream.read());
        assertEquals(25, stream.read());
        assertEquals(15, stream.read());
        assertEquals(100, stream.read());
        assertEquals(0, stream.read());
        assertEquals(0, stream.read());
        assertEquals(-128 & 0xff, stream.read());
        assertEquals(127, stream.read());
        assertEquals(0, stream.read());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read());
        // I fed it four buffers, so there should be -4
        allocator.check(-4);
    }

    public void testArrayRead() throws Throwable {
        final TestByteBufferAllocator allocator = new TestByteBufferAllocator(3);
        final ByteBufferInputStream stream = new ByteBufferInputStream(Streams.<ByteBuffer>getIteratorObjectSource(Arrays.<ByteBuffer>asList(
                ByteBuffer.wrap(new byte[] { 5, 100, 30, 12, -60, 25 }),
                ByteBuffer.wrap(new byte[] { 15 }),
                ByteBuffer.wrap(new byte[] { }),
                ByteBuffer.wrap(new byte[] { 100, 0, 0, -128, 127, 0 })).iterator()), allocator);
        assertEquals(5, stream.read());
        assertEquals(100, stream.read());
        assertEquals(30, stream.read());
        byte[] bytes = new byte[5];
        assertEquals(5, stream.read(bytes));
        assertTrue(Arrays.equals(new byte[] { 12, -60, 25, 15, 100 }, bytes));
        assertEquals(0, stream.read());
        bytes = new byte[15];
        Arrays.fill(bytes, (byte) 7);
        assertEquals(3, stream.read(bytes, 4, 3));
        assertTrue(Arrays.equals(new byte[] { 7, 7, 7, 7, 0, -128, 127, 7, 7, 7, 7, 7, 7, 7, 7 }, bytes));
        assertEquals(0, stream.read());
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read());
        // I fed it four buffers, so there should be -4
        allocator.check(-4);
    }
}
