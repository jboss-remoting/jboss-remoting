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

package org.jboss.remoting.stream;

import junit.framework.TestCase;
import java.util.ArrayList;
import java.util.List;
import java.nio.CharBuffer;
import org.jboss.remoting.test.support.TestCharBufferAllocator;
import org.jboss.remoting.test.support.LoggingHelper;

/**
 *
 */
public final class CharBufferWriterTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testBasic() throws Throwable {
        final TestCharBufferAllocator allocator = new TestCharBufferAllocator(7);
        final List<CharBuffer> list = new ArrayList<CharBuffer>();
        final ObjectSink<CharBuffer> sink = Streams.getCollectionObjectSink(list);
        final CharBufferWriter writer = new CharBufferWriter(sink, allocator);
        writer.append("Th");
        writer.append("blah e qui blah", 5, 10);
        writer.append('c');
        writer.write('k');
        writer.write(new char[] { ' ', 'b', 'r' });
        writer.write(new char[] { 'x', 'x', 'o', 'w', 'n', ' ', 'x' }, 2, 4);
        writer.write("fox jumps");
        writer.write("blah over the lazy dogs. blah", 4, 20);
        writer.flush();
        writer.close();
        final ObjectSource<CharBuffer> source = Streams.getIteratorObjectSource(list.iterator());
        CharBufferReader reader = new CharBufferReader(source, allocator);
        String s = "The quick brown fox jumps over the lazy dogs.";
        for (int i = 0; i < s.length(); i ++) {
            assertEquals("position = " + i, (char)s.charAt(i), (char)reader.read());
        }
        assertEquals(-1, reader.read());
        allocator.check(0);
    }
}
