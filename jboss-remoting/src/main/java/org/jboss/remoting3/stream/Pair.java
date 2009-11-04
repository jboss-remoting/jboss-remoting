/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.stream;

import java.io.Serializable;

/**
 * A serializable pair of values.
 *
 * @param <A> the type of the first value
 * @param <B> the type of the second value
 */
public final class Pair<A, B> implements Serializable {

    private static final long serialVersionUID = -1812076980977921946L;

    private final A a;
    private final B b;

    /**
     * Construct a new instance.
     *
     * @param a the first value
     * @param b the second value
     */
    public Pair(final A a, final B b) {
        this.a = a;
        this.b = b;
    }

    /**
     * Get the first value.
     *
     * @return the first value
     */
    public A getA() {
        return a;
    }

    /**
     * Get the second value.
     *
     * @return the second value
     */
    public B getB() {
        return b;
    }
}
