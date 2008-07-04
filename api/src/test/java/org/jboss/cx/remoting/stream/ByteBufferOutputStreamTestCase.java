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
import org.jboss.cx.remoting.test.support.TestByteBufferAllocator;
import org.jboss.cx.remoting.test.support.LoggingHelper;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public final class ByteBufferOutputStreamTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testBasic() throws Throwable {
        final TestByteBufferAllocator allocator = new TestByteBufferAllocator(4);
        final List<ByteBuffer> list = new ArrayList<ByteBuffer>();
        final ObjectSink<ByteBuffer> sink = Streams.getCollectionObjectSink(list);
        final ByteBufferOutputStream stream = new ByteBufferOutputStream(sink, allocator);
        stream.write(new byte[] { 6, 1, 5, 2, 4, 3, 2, 4, 1, 5, 0, 6 });
        stream.write(new byte[0]);
        stream.write(new byte[] { 4, 5, 6, 45, -20, 0, 0, 1, 12, 13, 19, 34 }, 3, 7);
        stream.write(new byte[] { 45, -20, 0, 0, 1, 12, 13 }, 4, 0);
        stream.write(0);
        stream.write(10);
        stream.flush();
        stream.close();
        final ByteBufferInputStream inputStream = new ByteBufferInputStream(Streams.getIteratorObjectSource(list.iterator()), allocator);
        assertEquals(6, inputStream.read());
        assertEquals(1, inputStream.read());
        assertEquals(5, inputStream.read());
        assertEquals(2, inputStream.read());
        assertEquals(4, inputStream.read());
        assertEquals(3, inputStream.read());
        assertEquals(2, inputStream.read());
        assertEquals(4, inputStream.read());
        assertEquals(1, inputStream.read());
        assertEquals(5, inputStream.read());
        assertEquals(0, inputStream.read());
        assertEquals(6, inputStream.read());
        assertEquals(45, inputStream.read());
        assertEquals(-20 & 0xff, inputStream.read());
        assertEquals(0, inputStream.read());
        assertEquals(0, inputStream.read());
        assertEquals(1, inputStream.read());
        assertEquals(12, inputStream.read());
        assertEquals(13, inputStream.read());
        assertEquals(0, inputStream.read());
        assertEquals(10, inputStream.read());
        assertEquals(-1, inputStream.read());
        inputStream.close();
        allocator.check(0);
    }
}
