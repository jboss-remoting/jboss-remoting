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
 * A qualified name for service registration.  A qualified name is a path-like structure comprised of a series of
 * zero or more name segments.  The string representation of a qualified name is a sequence of a forward slash
 * ({@code /}) followed by a non-empty URL-encoded name segment.
 */
public final class QualifiedName implements Comparable<QualifiedName>, Iterable<String> {

    /**
     * The root name.
     */
    public static final QualifiedName ROOT_NAME = new QualifiedName(new String[0]);

    private final String[] segments;

    /**
     * Create a new qualified name from the given name segments.
     *
     * @param nameSegments the name segments
     * @throws NullPointerException if {@code nameSegments} is {@code null} or if any element of that array is {@code null}
     * @throws IllegalArgumentException if an element of {@code nameSegments} is an empty string
     */
    public QualifiedName(final String[] nameSegments) throws NullPointerException, IllegalArgumentException {
        if (nameSegments == null) {
            throw new NullPointerException("segments is null");
        }
        String[] segments = nameSegments.clone();
        for (String s : segments) {
            if (s == null) {
                throw new NullPointerException("Null segment");
            }
            if (s.length() == 0) {
                throw new IllegalArgumentException("Empty segment");
            }
        }
        this.segments = segments;
    }

    /**
     * Compare this qualified name to another for equality.  Returns {@code true} if both names have the same number of segments
     * with the same content.
     *
     * @param o the object to compare to
     * @return {@code true} if the given object is a qualified name which is equal to this name
     */
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (! (o instanceof QualifiedName)) return false;
        final QualifiedName name = (QualifiedName) o;
        if (!Arrays.equals(segments, name.segments)) return false;
        return true;
    }

    /**
     * Get the hash code of this qualified name.  Equal to the return value of {@link Arrays#hashCode(Object[]) Arrays.hashCode(segments)}
     * where {@code segments} is the array of decoded segment strings.
     *
     * @return the hash code
     */
    public int hashCode() {
        return Arrays.hashCode(segments);
    }

    /**
     * Compare this qualified name to another.  Each segment is compared in turn; if they are equal then the comparison
     * carries on to the next segment.  If all leading segments are equal but one qualified name has more segments,
     * then the longer name is said to come after the shorter name.
     *
     * @param o the other name
     * @return {@code 0} if the elements are equal, {@code -1} if this name comes before the given name, or {@code 1} if
     *      this name comes after the given name
     */
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

    /**
     * Get the string representation of this qualified name.  The root name is "{@code /}"; all other names are comprised
     * of one or more consecutive character sequences of a forward slash followed by one or more URL-encoded characters.
     *
     * @return the string representation of this name
     */
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

    /**
     * Parse a qualified name.  A qualified name must consist of either a single forward slash ("{@code /}") or else
     * a series of path components, each comprised of a single forward slash followed by a URL-encoded series of non-forward-slash
     * characters.
     *
     * @param path the path
     * @return the qualified name
     */
    public static QualifiedName parse(String path) {
        List<String> decoded = new ArrayList<String>();
        final int len = path.length();
        if (len < 1) {
            throw new IllegalArgumentException("Empty path");
        }
        if (path.charAt(0) != '/') {
            throw new IllegalArgumentException("Relative paths are not allowed");
        }
        if (len == 1) {
            return ROOT_NAME;
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

    /**
     * Get an iterator over the sequence of strings.
     *
     * @return an iterator
     */
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

    /**
     * Get the number of segments in this name.
     *
     * @return the number of segments
     */
    public int length() {
        return segments.length;
    }
}
