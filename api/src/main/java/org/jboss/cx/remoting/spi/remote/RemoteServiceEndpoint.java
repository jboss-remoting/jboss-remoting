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
import org.jboss.cx.remoting.ClientSource;

/**
 * A remote service endpoint, which can be passed to remote endpoints.  Remote systems can then use the service endpoint
 * to acquire client endpoints, or they may pass it on to other systems.
 */
public interface RemoteServiceEndpoint<I, O> extends Closeable<RemoteServiceEndpoint<I, O>> {

    /**
     * Create a client endpoint for the service corresponding to this service endpoint.
     *
     * @return a client endpoint
     * @throws RemotingException if a client could not be opened
     */
    RemoteClientEndpoint<I, O> openClient() throws RemotingException;

    /**
     * Get a handle to this service endpoint.  The service endpoint will not auto-close as long as there is at least
     * one open handle,remote client endpoint, or client source.  If a handle is "leaked", it will be closed
     * automatically if/when the garbage collector invokes its {@link Object#finalize()} method, with a log message
     * warning of the leak.
     *
     * @return the handle
     * @throws RemotingException if a handle could not be acquired
     */
    Handle<RemoteServiceEndpoint<I, O>> getHandle() throws RemotingException;

    /**
     * Get a local client source which can be used to access this service.
     *
     * @return the client source
     */
    ClientSource<I, O> getClientSource() throws RemotingException;

    /**
     * Automatically close this service endpoint when all handles and local client source instances
     * are closed.
     */
    void autoClose() throws RemotingException;

    /**
     * Close this service endpoint immediately.
     */
    void close() throws RemotingException;

    /**
     * Add a handler that is called when the service endpoint is closed.
     *
     * @param handler the handler to be called
     */
    void addCloseHandler(final CloseHandler<RemoteServiceEndpoint<I, O>> handler);
}
