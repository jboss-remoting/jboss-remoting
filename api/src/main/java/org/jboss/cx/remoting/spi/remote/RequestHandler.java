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

/**
 * A request handler, which can be passed to remote endpoints.  Remote systems can then use the handler
 * to make invocations, or they may forward a handler on to other remote systems.
 */
public interface RequestHandler extends Closeable<RequestHandler> {

    /**
     * Receive a one-way request from a remote system.  This method is intended to be called by protocol handlers.  No
     * reply will be sent back to the client.
     *
     * @param request the request
     */
    void receiveRequest(Object request);

    /**
     * Receive a request from a remote system.  This method is intended to be called by protocol handlers.  If the
     * request cannot be accepted for some reason, the
     * {@link ReplyHandler#handleException(String, Throwable)}
     * method is called immediately.
     *
     * @param request the request
     * @param replyHandler a handler for the reply
     * @return a context which may be used to cancel the request
     */
    RemoteRequestContext receiveRequest(Object request, ReplyHandler replyHandler);

    /**
     * Get a handle to this request handler.  The request handler will not auto-close as long as there is at least
     * one open handle.  If a handle is "leaked", it will be closed
     * automatically if/when the garbage collector invokes its {@link Object#finalize()} method, with a log message
     * warning of the leak.
     *
     * @return the handle
     * @throws RemotingException if a handle could not be acquired
     */
    Handle<RequestHandler> getHandle() throws RemotingException;

    /**
     * Close this request handler.  The outcome of any outstanding requests is not defined, though implementations
     * should make an effort to cancel any outstanding requests.
     *
     * @throws RemotingException if the client endpoint could not be closed
     */
    void close() throws RemotingException;

    /**
     * Add a handler that is called when the request handler is closed.
     *
     * @param handler the handler to be called
     */
    void addCloseHandler(final CloseHandler<? super RequestHandler> handler);
}
