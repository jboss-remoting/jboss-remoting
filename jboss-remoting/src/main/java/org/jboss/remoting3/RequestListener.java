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

import java.util.EventListener;

/**
 * A request listener.  Implementations of this interface will reply to client requests.
 *
 * @param <I> the request type
 * @param <O> the reply type
 *
 * @remoting.implement
 * @apiviz.landmark
 */
public interface RequestListener<I, O> extends EventListener {
    /**
     * Handle a request.  If this method throws {@code RemoteExecutionException}, then that exception is passed
     * back to the caller and the request is marked as complete.  Otherwise, the request
     * listener must send back either a reply (using the {@code sendReply()} method on the {@code RequestContext}) or
     * an exception (using the {@code sendException()} method on the {@code RequestContext}).
     *
     * @param context the context on which a reply may be sent
     * @param request the received request
     *
     * @throws RemoteExecutionException if the execution failed in some way
     */
    void handleRequest(RequestContext<O> context, I request) throws RemoteExecutionException;

    /**
     * Handle the client closing.  Free up any resources.  This method is called after the close has occurred,
     * so exceptions thrown will be ignored.
     */
    void handleClose();
}
