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

package org.jboss.cx.remoting.spi.marshal;

import junit.framework.TestCase;
import java.io.IOException;
import java.util.Collections;
import java.util.Arrays;
import org.jboss.cx.remoting.test.support.LoggingHelper;

/**
 *
 */
public final class ObjectResolverTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public static final class ThingOne {

    }

    public static final class ThingTwo {

    }

    public static final class ResolverOne implements ObjectResolver {

        private static final long serialVersionUID = 6121192940632885123L;

        public Object writeReplace(final Object original) throws IOException {
            if (original instanceof ThingOne) {
                return "ThingOne";
            } else {
                return original;
            }
        }

        public Object readResolve(final Object original) throws IOException {
            if (original instanceof String && "ThingOne".equals(original)) {
                return new ThingOne();
            } else {
                return original;
            }
        }
    }

    public static final class ResolverTwo implements ObjectResolver {

        private static final long serialVersionUID = 7833685858039930273L;

        public Object writeReplace(final Object original) throws IOException {
            if (original instanceof ThingTwo) {
                return "ThingTwo";
            } else {
                return original;
            }
        }

        public Object readResolve(final Object original) throws IOException {
            if (original instanceof String && "ThingTwo".equals(original)) {
                return new ThingTwo();
            } else {
                return original;
            }
        }
    }

    public void testCompositeResolver() throws Throwable {
        final CompositeObjectResolver compositeObjectResolver = new CompositeObjectResolver(Arrays.asList(new ResolverOne(), new ResolverTwo()));
        assertEquals(compositeObjectResolver.writeReplace("Test"), "Test");
        assertEquals(compositeObjectResolver.writeReplace(new ThingOne()), "ThingOne");
        assertEquals(compositeObjectResolver.writeReplace(new ThingTwo()), "ThingTwo");
        assertEquals(compositeObjectResolver.readResolve("Test"), "Test");
        assertTrue(compositeObjectResolver.readResolve("ThingOne") instanceof ThingOne);
        assertTrue(compositeObjectResolver.readResolve("ThingTwo") instanceof ThingTwo);
    }
}
