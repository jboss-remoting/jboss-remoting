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

package org.jboss.remoting3.taglet;

import java.util.Map;

import com.sun.javadoc.Tag;
import com.sun.tools.doclets.Taglet;

public abstract class RemotingTypeTaglet implements Taglet {

    public boolean inField() {
        return false;
    }

    public boolean inConstructor() {
        return false;
    }

    public boolean inMethod() {
        return false;
    }

    public boolean inOverview() {
        return false;
    }

    public boolean inPackage() {
        return false;
    }

    public boolean inType() {
        return true;
    }

    public boolean isInlineTag() {
        return false;
    }

    public String toString(final Tag[] tags) {
        return tags.length > 0 ? toString(tags[0]) : "";
    }

    private static void add(Map<String, Taglet> tagletMap, Taglet taglet) {
        tagletMap.put(taglet.getName(), taglet);
    }

    public static void register(Map<String, Taglet> tagletMap) {
        add(tagletMap, new RemotingConsumeTaglet());
        add(tagletMap, new RemotingImplementTaglet());
        add(tagletMap, new RemotingInternalTaglet());
    }
}
