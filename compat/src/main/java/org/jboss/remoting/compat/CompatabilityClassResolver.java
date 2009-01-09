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

import org.jboss.marshalling.AbstractClassResolver;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 *
 */
public abstract class CompatabilityClassResolver extends AbstractClassResolver {

    private static final Map<Class<?>, String> CLASS_WRITE_MAP;
    private static final Map<String, Class<?>> CLASS_READ_MAP;

    static {
        final Map<Class<?>, String> classWriteMap = new HashMap<Class<?>, String>();
        classWriteMap.put(CompatabilityInvocationRequest.class, "org.jboss.remoting.InvocationRequest");
        classWriteMap.put(CompatabilityInvocationResponse.class, "org.jboss.remoting.InvocationReply");
        classWriteMap.put(CompatibilityInvokerLocator.class, "org.jboss.remoting.InvokerLocator");
        classWriteMap.put(CompatibilityHome.class, "org.jboss.remoting.Home");
        CLASS_WRITE_MAP = Collections.unmodifiableMap(classWriteMap);
        final Map<String, Class<?>> classReadMap = new HashMap<String, Class<?>>();
        for (Map.Entry<Class<?>, String> entry : classWriteMap.entrySet()) {
            classReadMap.put(entry.getValue(), entry.getKey());
        }
        CLASS_READ_MAP = Collections.unmodifiableMap(classReadMap);
    }

    public String getClassName(final Class<?> clazz) throws IOException {
        final String name = CLASS_WRITE_MAP.get(clazz);
        return name == null ? clazz.getName() : name;
    }

    protected Class<?> loadClass(final String name) throws ClassNotFoundException {
        final Class<?> clazz = CLASS_READ_MAP.get(name);
        return clazz == null ? super.loadClass(name) : clazz;
    }
}
