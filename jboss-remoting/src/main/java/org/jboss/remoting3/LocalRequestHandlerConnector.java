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

import java.util.concurrent.Executor;
import org.jboss.remoting3.spi.LocalRequestHandler;
import org.jboss.remoting3.spi.RemoteRequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;

final class LocalRequestHandlerConnector implements RequestHandlerConnector {

    private final LocalRequestHandler localHandler;
    private final Executor executor;

    LocalRequestHandlerConnector(final LocalRequestHandler localHandler, final Executor executor) {
        this.localHandler = localHandler;
        this.executor = executor;
    }

    public Cancellable createRequestHandler(final Result<RemoteRequestHandler> result) throws SecurityException {
        final LocalRemoteRequestHandler handler = new LocalRemoteRequestHandler(localHandler, null, OptionMap.EMPTY, OptionMap.EMPTY, executor);
        localHandler.addCloseHandler(SpiUtils.closingCloseHandler(handler));
        result.setResult(handler);
        return IoUtils.nullCancellable();
    }
}
