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
import org.jboss.remoting3.spi.LocalRequestHandler;
import org.jboss.remoting3.spi.RemoteRequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;

final class LocalConnectionHandler implements ConnectionHandler {

    private final ConnectionHandlerContext connectionHandlerContext;
    private final OptionMap connectionOptionMap;

    public LocalConnectionHandler(final ConnectionHandlerContext connectionHandlerContext, final OptionMap connectionOptionMap) {
        this.connectionHandlerContext = connectionHandlerContext;
        this.connectionOptionMap = connectionOptionMap;
    }

    public Cancellable open(final String serviceType, final String groupName, final Result<RemoteRequestHandler> result, final ClassLoader classLoader, final OptionMap optionMap) {
        final LocalRequestHandler handler = connectionHandlerContext.openService(serviceType, groupName, optionMap);
        if (handler == null) {
            result.setException(new ServiceNotFoundException(ServiceURI.create(serviceType, groupName, null)));
        } else {
            final LocalRemoteRequestHandler requestHandler = new LocalRemoteRequestHandler(handler, classLoader, optionMap, connectionOptionMap, connectionHandlerContext.getConnectionProviderContext().getExecutor());
            result.setResult(requestHandler);
        }
        return IoUtils.nullCancellable();
    }

    public RequestHandlerConnector createConnector(final LocalRequestHandler localHandler) {
        return new LocalRequestHandlerConnector(localHandler, connectionHandlerContext.getConnectionProviderContext().getExecutor());
    }

    public void close() throws IOException {
        connectionHandlerContext.remoteClosed();
    }
}
