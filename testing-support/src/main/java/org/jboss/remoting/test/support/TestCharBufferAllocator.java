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

package org.jboss.remoting.test.support;

import java.nio.CharBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.xnio.BufferAllocator;
import junit.framework.TestCase;

/**
 *
 */
public final class TestCharBufferAllocator implements BufferAllocator<CharBuffer> {

    private final AtomicInteger count = new AtomicInteger();
    private final int size;

    public TestCharBufferAllocator(final int size) {
        this.size = size;
    }

    public CharBuffer allocate() {
        final CharBuffer buffer = CharBuffer.allocate(size);
        count.incrementAndGet();
        return buffer;
    }

    public void free(final CharBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer is null");
        }
        count.decrementAndGet();
    }

    public void check(int expectCount) {
        TestCase.assertEquals(expectCount, count.get());
    }
}