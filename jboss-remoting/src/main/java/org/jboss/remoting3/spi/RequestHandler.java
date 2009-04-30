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

package org.jboss.remoting3.spi;

import org.jboss.remoting3.HandleableCloseable;

/**
 * A request handler.
 * <p>
 * This is an internal Remoting interface, intended to be implemented only by Remoting internals and protocol implementations.
 * It should not be used or implemented by end-users.  Members may be added without notice.  Applications should instead use
 * the {@link org.jboss.remoting3.Client Client} and {@link org.jboss.remoting3.RequestListener RequestListener} interfaces.
 */
public interface RequestHandler extends HandleableCloseable<RequestHandler> {

    /**
     * Receive a request from a remote system.  This method is intended to be called by protocol handlers.  If the
     * request cannot be accepted for some reason, the
     * {@link ReplyHandler#handleException(java.io.IOException)}
     * method is called immediately.
     *
     * @param request the request
     * @param replyHandler a handler for the reply
     * @return a context which may be used to cancel the request
     */
    RemoteRequestContext receiveRequest(Object request, ReplyHandler replyHandler);
}
