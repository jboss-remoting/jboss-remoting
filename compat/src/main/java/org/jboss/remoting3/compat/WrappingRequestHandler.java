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

package org.jboss.remoting3.compat;

import org.jboss.remoting3.spi.RemoteRequestHandler;
import org.jboss.remoting3.spi.RemoteReplyHandler;
import java.util.concurrent.Executor;
import org.jboss.xnio.Cancellable;

/**
 * A request handler which wraps a Remoting 3-style plain request (or an instance of {@link org.jboss.remoting3.compat.Request Request}
 * with a Remoting 2-style invocation request.
 */
public final class WrappingRequestHandler extends AbstractAutoCloseable<RemoteRequestHandler> implements RemoteRequestHandler {

    private final RemoteRequestHandler next;

    public WrappingRequestHandler(final RemoteRequestHandler next, final Executor executor) {
        super(executor);
        this.next = next;
    }

    public Cancellable receiveRequest(final Object obj, final RemoteReplyHandler replyHandler) {
        final CompatabilityInvocationRequest cir = new CompatabilityInvocationRequest();
        if (obj instanceof Request) {
            final Request request = (Request) obj;
            cir.setArg(request.getBody());
            // wtf?
            //noinspection unchecked
            cir.setRequestPayload(request.getMetadata());
        } else {
            cir.setArg(obj);
        }
        return next.receiveRequest(cir, new UnwrappingReplyHandler(replyHandler));
    }
}
