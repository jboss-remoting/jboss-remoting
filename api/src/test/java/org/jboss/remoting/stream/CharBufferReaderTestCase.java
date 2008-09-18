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
import org.jboss.remoting.test.support.TestCharBufferAllocator;
import org.jboss.remoting.test.support.LoggingHelper;
import java.util.Arrays;
import java.nio.CharBuffer;

/**
 *
 */
public final class CharBufferReaderTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testBasic() throws Throwable {
        final TestCharBufferAllocator allocator = new TestCharBufferAllocator(10);
        final ObjectSource<CharBuffer> source = Streams.getIteratorObjectSource(Arrays.asList(
                CharBuffer.wrap("The quick brown "),
                CharBuffer.wrap("fox j"),
                CharBuffer.wrap("u"),
                CharBuffer.allocate(0),
                CharBuffer.wrap("mps over the la"),
                CharBuffer.wrap("zy dogs.")
        ).iterator());
        CharBufferReader reader = new CharBufferReader(source, allocator);
        String s = "The quick brown fox jumps over the lazy dogs.";
        for (int i = 0; i < s.length(); i ++) {
            assertEquals(s.charAt(i), reader.read());
        }
        assertEquals(-1, reader.read());
        allocator.check(-6);
    }
}
