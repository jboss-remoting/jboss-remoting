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

package org.jboss.cx.remoting.spi.remote;

import org.jboss.cx.remoting.Closeable;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.CloseHandler;
import java.io.IOException;

/**
 * A request handler source, which can be passed to remote endpoints.  Remote systems can then use the handler source
 * to acquire request handlers, or they may pass it on to other systems.  Acquiring a request handler using this method
 * has the advantage that a round trip to the remote side is not necessary; the local side can spawn a request handler
 * and simply notify the remote side of the change.
 */
public interface RequestHandlerSource extends Closeable<RequestHandlerSource> {

    /**
     * Create a request handler for the service corresponding to this request handler source.
     *
     * @return a request handler
     * @throws RemotingException if a client could not be opened
     */
    Handle<RequestHandler> createRequestHandler() throws IOException;

    /**
     * Get a handle to this request handler source.  The request handler source will not auto-close as long as there is at least
     * one open handle, or request handler.  If a handle is "leaked", it will be closed
     * automatically if/when the garbage collector invokes its {@link Object#finalize()} method, with a log message
     * warning of the leak.
     *
     * @return the handle
     * @throws RemotingException if a handle could not be acquired
     */
    Handle<RequestHandlerSource> getHandle() throws IOException;

    /**
     * Close this request handler source immediately.
     */
    void close() throws IOException;

    /**
     * Add a handler that is called when the request handler source is closed.
     *
     * @param handler the handler to be called
     */
    void addCloseHandler(final CloseHandler<? super RequestHandlerSource> handler);
}
