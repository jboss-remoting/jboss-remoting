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

import org.jboss.remoting.Endpoint;
import org.jboss.remoting.Client;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.Handle;
import org.jboss.xnio.IoUtils;
import java.io.IOException;
import java.lang.reflect.Proxy;

/**
 * A static class which can be used to create transporter proxies.
 */
public final class Transporter {

    private Transporter() {
    }

    /**
     * Create a transporter for an object instance.  The given type must be an interface type.  The returned object
     * is a serializable proxy that can be sent to other endpoints as a part of a request or a reply.
     *
     * @param endpoint the endpoint to anchor the transporter to
     * @param interfaceType the type of the interface to use
     * @param instance the instance to which invocations will be sent
     * @return a transporter proxy
     * @throws IOException if an error occurs
     */
    public static <T> T createTransporter(Endpoint endpoint, Class<T> interfaceType, T instance) throws IOException {
        boolean ok = false;
        final Handle<RequestHandler> requestHandlerHandle = endpoint.createRequestHandler(new TransporterRequestListener<T>(instance));
        try {
            final Client<TransporterInvocation,Object> client = endpoint.createClient(requestHandlerHandle.getResource());
            try {
                requestHandlerHandle.close();
                final T proxy = createProxy(interfaceType, client);
                ok = true;
                return proxy;
            } finally {
                if (! ok) {
                    IoUtils.safeClose(client);
                }
            }
        } finally {
            IoUtils.safeClose(requestHandlerHandle);
        }
    }

    @SuppressWarnings({ "unchecked" })
    private static <T> T createProxy(final Class<T> interfaceType, final Client<TransporterInvocation, Object> client) {
        return (T) Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] { interfaceType }, new TransporterInvocationHandler(client));
    }
}
