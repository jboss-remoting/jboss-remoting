/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3;

import java.io.IOException;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;

final class LocalConnectionHandler implements ConnectionHandler {

    private final ConnectionHandlerContext connectionHandlerContext;

    public LocalConnectionHandler(final ConnectionHandlerContext connectionHandlerContext) {
        this.connectionHandlerContext = connectionHandlerContext;
    }

    public Cancellable open(final String serviceType, final String groupName, final Result<RequestHandler> result) {
        // todo: support for call-by-value
        final RequestHandler handler = connectionHandlerContext.openService(serviceType, groupName, OptionMap.EMPTY);
        if (handler == null) {
            result.setException(new ServiceNotFoundException(ServiceURI.create(serviceType, groupName, null)));
        } else {
            result.setResult(handler);
        }
        return IoUtils.nullCancellable();
    }

    public RequestHandlerConnector createConnector(final RequestHandler localHandler) {
        return new RequestHandlerConnector() {
            public Cancellable createRequestHandler(final Result<RequestHandler> result) throws SecurityException {
                result.setResult(localHandler);
                return IoUtils.nullCancellable();
            }
        };
    }

    public void close() throws IOException {
        connectionHandlerContext.remoteClosed();
    }
}
