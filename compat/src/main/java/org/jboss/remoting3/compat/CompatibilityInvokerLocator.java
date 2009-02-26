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

package org.jboss.remoting3.compat;

import java.net.URI;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.util.ArrayList;
import java.util.Map;

/**
 *
 */
public class CompatibilityInvokerLocator implements Serializable {

    private static final long serialVersionUID = -4977622166779282521L;

    private static final ObjectStreamField[] serialPersistentFields = {
            new ObjectStreamField("protocol", String.class),
            new ObjectStreamField("host", String.class),
            new ObjectStreamField("connectHomes", ArrayList.class),
            new ObjectStreamField("homes", ArrayList.class),
            new ObjectStreamField("port", int.class),
            new ObjectStreamField("path", String.class),
            new ObjectStreamField("query", String.class),
            new ObjectStreamField("parameters", Map.class),
            new ObjectStreamField("uri", String.class),
            new ObjectStreamField("originalURL", String.class),
            new ObjectStreamField("homeInUse", CompatibilityHome.class),
    };

    private transient URI uri;

    public CompatibilityInvokerLocator() {
    }

    public CompatibilityInvokerLocator(final URI uri) {
        this.uri = uri;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(final URI uri) {
        this.uri = uri;
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        final ObjectInputStream.GetField fields = stream.readFields();

    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        final ObjectOutputStream.PutField fields = stream.putFields();
        fields.put("protocol", uri.getScheme());
        fields.put("host", uri.getHost());
        fields.put("port", uri.getPort());
        fields.put("path", uri.getPath());
        fields.put("query", uri.getQuery());
        fields.put("uri", uri.toString());
        fields.put("originalURL", uri.toString());
        
        stream.writeFields();
    }
}
