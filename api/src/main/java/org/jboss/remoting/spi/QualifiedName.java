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

package org.jboss.remoting.spi;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/**
 *
 */
public final class QualifiedName implements Comparable<QualifiedName>, Iterable<String> {
    private final String[] segments;

    public QualifiedName(final String[] segments) {
        if (segments == null) {
            throw new NullPointerException("segments is null");
        }
        for (String s : segments) {
            if (s == null) {
                throw new NullPointerException("a segment is null");
            }
        }
        this.segments = segments;
    }

    public boolean equals(final Object o) {
        if (this == o) return true;
        if (! (o instanceof QualifiedName)) return false;
        final QualifiedName name = (QualifiedName) o;
        if (!Arrays.equals(segments, name.segments)) return false;
        return true;
    }

    public int hashCode() {
        return Arrays.hashCode(segments);
    }

    public int compareTo(final QualifiedName o) {
        if (this == o) return 0;
        String[] a = segments;
        String[] b = o.segments;
        final int alen = a.length;
        final int blen = b.length;
        for (int i = 0; i < alen && i < blen; i ++) {
            final int cmp = a[i].compareTo(b[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        if (alen < blen) {
            return -1;
        } else if (alen > blen) {
            return 1;
        } else {
            return 0;
        }
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (segments.length == 0) {
            return "/";
        } else for (String segment : segments) {
            try {
                builder.append('/');
                builder.append(URLEncoder.encode(segment, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                // cannot happen
                throw new IllegalStateException(e);
            }
        }
        return builder.toString();
    }

    public static QualifiedName parse(String path) {
        List<String> decoded = new ArrayList<String>();
        final int len = path.length();
        if (len < 1) {
            throw new IllegalArgumentException("Empty path");
        }
        if (path.charAt(0) != '/') {
            throw new IllegalArgumentException("Relative paths are not allowed");
        }
        int segStart = 0;
        int segEnd;
        do {
            segEnd = path.indexOf('/', segStart + 1);
            String segment = segEnd == -1 ? path.substring(segStart + 1) : path.substring(segStart + 1, segEnd);
            if (segment.length() == 0) {
                throw new IllegalArgumentException(segEnd == -1 ? "Invalid trailing slash" : "Empty segment in path");
            }
            try {
                decoded.add(URLDecoder.decode(segment, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                // cannot happen
                throw new IllegalStateException(e);
            }
            segStart = segEnd;
        } while (segEnd != -1);
        return new QualifiedName(decoded.toArray(new String[decoded.size()]));
    }

    public Iterator<String> iterator() {
        return new Iterator<String>() {
            int i;

            public boolean hasNext() {
                return i < segments.length;
            }

            public String next() {
                try {
                    return segments[i++];
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new NoSuchElementException("next() past end");
                }
            }

            public void remove() {
                throw new UnsupportedOperationException("remove()");
            }
        };
    }

    public int length() {
        return segments.length;
    }
}
