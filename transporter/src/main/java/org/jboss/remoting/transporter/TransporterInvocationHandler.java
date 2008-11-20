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

package org.jboss.remoting.transporter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.remoting.Client;
import org.jboss.remoting.RemoteExecutionException;
import org.jboss.xnio.IoUtils;

/**
 * The transporter reflection invocation handler.
 */
public final class TransporterInvocationHandler implements InvocationHandler {
    private final Client<TransporterInvocation, Object> client;
    private final ConcurrentMap<Method, TransporterMethodDescriptor> descriptorCache = new ConcurrentHashMap<Method, TransporterMethodDescriptor>();

    public TransporterInvocationHandler(final Client<TransporterInvocation, Object> client) {
        this.client = client;
    }

    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        try {
            final TransporterMethodDescriptor descriptor;
            final TransporterMethodDescriptor cachedDescriptor = descriptorCache.get(method);
            if (cachedDescriptor == null) {
                final TransporterMethodDescriptor newDescriptor = new TransporterMethodDescriptor(method.getName(), method.getParameterTypes());
                final TransporterMethodDescriptor suddenDescriptor = descriptorCache.putIfAbsent(method, newDescriptor);
                if (suddenDescriptor != null) {
                    descriptor = suddenDescriptor;
                } else {
                    descriptor = newDescriptor;
                }
            } else {
                descriptor = cachedDescriptor;
            }
            return client.invoke(new TransporterInvocation(descriptor, args));
        } catch (RemoteExecutionException e) {
            throw e.getCause();
        } catch (IOException e) {
            throw new IllegalStateException("Method invocation failed", e);
        }
    }

    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            IoUtils.safeClose(client);
        }
    }
}
