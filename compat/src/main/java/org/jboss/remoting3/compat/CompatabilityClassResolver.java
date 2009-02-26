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

import org.jboss.marshalling.AbstractClassResolver;
import org.jboss.remoting3.Client;
import java.io.IOException;
import java.io.InvalidClassException;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

/**
 * A base class resolver which maps Remoting 2 classes to their compatible placeholders.
 */
public abstract class CompatabilityClassResolver extends AbstractClassResolver {

    private static final Map<Class<?>, String> CLASS_WRITE_MAP;
    private static final Map<String, Class<?>> CLASS_READ_MAP;
    private static final Set<Class<?>> REMOTING_2_BLACKLIST;

    static {
        final Map<Class<?>, String> classWriteMap = new HashMap<Class<?>, String>();

        classWriteMap.put(CompatibilityClient.class, "org.jboss.remoting.Client");
        classWriteMap.put(CompatabilityInvocationRequest.class, "org.jboss.remoting.InvocationRequest");
        classWriteMap.put(CompatabilityInvocationResponse.class, "org.jboss.remoting.InvocationReply");
        classWriteMap.put(CompatibilityInvokerLocator.class, "org.jboss.remoting.InvokerLocator");
        classWriteMap.put(CompatibilityHome.class, "org.jboss.remoting.Home");

        classWriteMap.put(CompatibilityCallback.class, "org.jboss.remoting.callback.Callback");

        CLASS_WRITE_MAP = Collections.unmodifiableMap(classWriteMap);
        final Map<String, Class<?>> classReadMap = new HashMap<String, Class<?>>();
        for (Map.Entry<Class<?>, String> entry : classWriteMap.entrySet()) {
            classReadMap.put(entry.getValue(), entry.getKey());
        }
        CLASS_READ_MAP = Collections.unmodifiableMap(classReadMap);

        final Set<Class<?>> remoting2BlackList = new HashSet<Class<?>>();
        remoting2BlackList.add(Client.class);
        REMOTING_2_BLACKLIST = Collections.unmodifiableSet(remoting2BlackList);
    }

    public String getClassName(final Class<?> clazz) throws IOException {
        if (REMOTING_2_BLACKLIST.contains(clazz)) {
            throw new InvalidClassException(clazz.getName(), "Instances of this class may not be sent to a Remoting 2 client or server"); 
        }
        final String name = CLASS_WRITE_MAP.get(clazz);
        return name == null ? clazz.getName() : name;
    }

    protected Class<?> loadClass(final String name) throws ClassNotFoundException {
        final Class<?> clazz = CLASS_READ_MAP.get(name);
        return clazz == null ? super.loadClass(name) : clazz;
    }
}
