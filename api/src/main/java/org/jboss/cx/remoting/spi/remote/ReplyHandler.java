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

/**
 * A handler for replies from a request.  The handler should respect the first invocation made on it, and ignore
 * any subsequent invocations.
 */
public interface ReplyHandler<O> {

    /**
     * Handle a successful reply.
     *
     * @param reply the reply
     */
    void handleReply(O reply);

    /**
     * Handle a remote exception.
     *
     * @param cause the cause
     */
    void handleException(Throwable cause);

    /**
     * Handle a cancellation request.
     */
    void handleCancellation();
}
