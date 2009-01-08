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

import junit.framework.TestCase;
import org.jboss.remoting.test.support.LoggingHelper;
import org.jboss.remoting.QualifiedName;
import java.util.Iterator;

/**
 *
 */
public final class NameTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testParseEmpty() {
        boolean ok = false;
        try {
            QualifiedName.parse("");
        } catch (IllegalArgumentException e) {
            assertEquals("Wrong exception message", "Empty path", e.getMessage());
            ok = true;
        }
        assertTrue("Exception not thrown", ok);
    }

    public void testParseRelative() {
        boolean ok = false;
        try {
            QualifiedName.parse("some relative path/foo/bar");
        } catch (IllegalArgumentException e) {
            assertEquals("Wrong exception message", "Relative paths are not allowed", e.getMessage());
            ok = true;
        }
        assertTrue("Exception not thrown", ok);
    }

    public void testParseRoot() {
        final Iterator<String> i = QualifiedName.parse("/").iterator();
        assertFalse(i.hasNext());
    }

    public void testParseOneLevel() {
        final Iterator<String> i = QualifiedName.parse("/firstElement").iterator();
        assertTrue(i.hasNext());
        assertEquals("Wrong segment name", "firstElement", i.next());
        assertFalse(i.hasNext());
    }

    public void testParseTwoLevel() {
        final Iterator<String> i = QualifiedName.parse("/firstElement/secondElement").iterator();
        assertTrue(i.hasNext());
        assertEquals("Wrong segment name", "firstElement", i.next());
        assertTrue(i.hasNext());
        assertEquals("Wrong segment name", "secondElement", i.next());
        assertFalse(i.hasNext());
    }

    public void testParseManyLevel() {
        final Iterator<String> i = QualifiedName.parse("/firstElement/secondElement/boo/test+with+spaces%20test").iterator();
        assertTrue(i.hasNext());
        assertEquals("Wrong segment name", "firstElement", i.next());
        assertTrue(i.hasNext());
        assertEquals("Wrong segment name", "secondElement", i.next());
        assertTrue(i.hasNext());
        assertEquals("Wrong segment name", "boo", i.next());
        assertTrue(i.hasNext());
        assertEquals("Wrong segment name", "test with spaces test", i.next());
        assertFalse(i.hasNext());
    }
}
