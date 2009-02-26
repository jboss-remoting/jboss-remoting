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

import java.io.Serializable;
import java.io.ObjectStreamField;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

/**
 *
 */
public class CompatabilityInvocationRequest implements Serializable {
    private static final long serialVersionUID = -6719842238864057289L;

    private static final ObjectStreamField[] serialPersistentFields = {
            new ObjectStreamField("sessionId", String.class),
            new ObjectStreamField("subsystem", String.class),
            new ObjectStreamField("arg", Object.class),
            new ObjectStreamField("requestPayload", Map.class),
            new ObjectStreamField("returnPayload", Map.class),
            new ObjectStreamField("locator", CompatibilityInvokerLocator.class),
    };

    private String sessionId;
    private String subsystem;
    private Object arg;
    private Map<Object, Object> requestPayload;
    private Map<Object, Object> returnPayload;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(final String subsystem) {
        this.subsystem = subsystem;
    }

    public Object getArg() {
        return arg;
    }

    public void setArg(final Object arg) {
        this.arg = arg;
    }

    public Map<Object, Object> getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(final Map<Object, Object> requestPayload) {
        this.requestPayload = requestPayload;
    }

    public Map<Object, Object> getReturnPayload() {
        return returnPayload;
    }

    public void setReturnPayload(final Map<Object, Object> returnPayload) {
        this.returnPayload = returnPayload;
    }

    @SuppressWarnings({ "unchecked" })
    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        final ObjectInputStream.GetField fields = stream.readFields();
        sessionId = (String) fields.get("sessionId", "");
        subsystem = (String) fields.get("subsystem", "");
        arg = fields.get("arg", "");
        requestPayload = (Map<Object, Object>) fields.get("requestPayload", null);
        returnPayload = (Map<Object, Object>) fields.get("returnPayload", null);
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        final ObjectOutputStream.PutField fields = stream.putFields();
        fields.put("sessionId", sessionId);
        fields.put("subsystem", subsystem);
        fields.put("arg", arg);
        fields.put("requestPayload", requestPayload);
        fields.put("returnPayload", returnPayload);
        stream.writeFields();
    }
}
