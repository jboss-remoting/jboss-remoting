/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.remoting3.remote;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.xnio.Pool;
import org.xnio.Pooled;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class DebuggingBufferPool implements Pool<ByteBuffer> {
    private final Pool<ByteBuffer> delegate;

    DebuggingBufferPool(final Pool<ByteBuffer> delegate) {
        this.delegate = delegate;
    }

    public Pooled<ByteBuffer> allocate() {
        final Pooled<ByteBuffer> real = delegate.allocate();
        final StackTraceElement first = findElement(new Throwable().getStackTrace());
        return new Pooled<ByteBuffer>() {
            private final ArrayList<StackTraceElement> users = new ArrayList<StackTraceElement>(0);
            private boolean freed;

            public void discard() {
                synchronized (this) {
                    users.clear();
                    freed = true;
                }
                real.discard();
            }

            public void free() {
                synchronized (this) {
                    users.clear();
                    freed = true;
                }
                real.free();
            }

            public ByteBuffer getResource() throws IllegalStateException {
                final ByteBuffer buffer = real.getResource();
                synchronized (this) {
                    users.add(findElement(new Throwable().getStackTrace()));
                    return buffer;
                }
            }

            protected void finalize() throws Throwable {
                synchronized (this) {
                    if (! freed) {
                        StringBuilder b = new StringBuilder();
                        b.append("Leaked a buffer which was allocated at: ");
                        format(b, first);
                        b.append(" and used at:");
                        for (StackTraceElement user : users) {
                            b.append('\n');
                            b.append('\t');
                            format(b, user);
                        }
                        RemoteLogger.log.info(b.toString());
                        discard();
                        super.finalize();
                    }
                }
            }
        };
    }

    static StackTraceElement findElement(StackTraceElement[] elements) {
        String className;
        for (final StackTraceElement element : elements) {
            className = element.getClassName();
            if (className == null) continue;
            if (className.startsWith(DebuggingBufferPool.class.getName())) {
                continue;
            }
            if ("allocate".equals(element.getMethodName())) {
                continue;
            }
            return element;
        }
        return new StackTraceElement("Unknown user", "", "", 0);
    }

    static void format(StringBuilder b, StackTraceElement e) {
        b.append(e.getClassName());
        b.append('#');
        b.append(e.getMethodName());
        b.append('(');
        final String fileName = e.getFileName();
        b.append(fileName == null ? "<unknown file>" : fileName);
        b.append(':');
        final int lineNumber = e.getLineNumber();
        if (lineNumber == -2) {
            b.append("<native>");
        } else if (lineNumber == -1) {
            b.append("<unknown line>");
        } else {
            b.append(lineNumber);
        }
        b.append(')');
    }
}
