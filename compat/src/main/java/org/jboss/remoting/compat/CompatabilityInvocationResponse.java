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

package org.jboss.remoting.compat;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

/**
 *
 */
public final class CompatabilityInvocationResponse<T> implements Serializable {
    private static final long serialVersionUID = 1324503813652865685L;

    private final String sessionId;
    private final Object result;
    private final boolean isException;
    private Map<Object, Object> payload;

    public CompatabilityInvocationResponse(final String sessionId, final Object result, final boolean isException, final Map<Object, Object> payload) {
        this.sessionId = sessionId;
        this.result = result;
        this.isException = isException;
        this.payload = payload;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Object getResult() {
        return result;
    }

    public boolean isException() {
        return isException;
    }

    public Map<Object, Object> getPayload() {
        return payload;
    }

    public void setPayload(final Map<Object, Object> payload) {
        this.payload = payload;
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        final ObjectInputStream.GetField fields = stream.readFields();
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        final ObjectOutputStream.PutField fields = stream.putFields();

        stream.writeFields();
    }
}
