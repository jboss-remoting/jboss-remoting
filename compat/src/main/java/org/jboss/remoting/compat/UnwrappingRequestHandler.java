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

package org.jboss.remoting.compat;

import org.jboss.remoting.spi.AbstractAutoCloseable;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.RemoteRequestContext;
import org.jboss.remoting.spi.ReplyHandler;
import org.jboss.remoting.spi.SpiUtils;
import org.jboss.remoting.RemoteRequestException;
import org.jboss.xnio.log.Logger;
import java.util.concurrent.Executor;
import java.util.Map;
import java.io.IOException;

/**
 * A request handler which unwraps a Remoting 2-style invocation request to a Remoting 3-style plain object
 * or {@link org.jboss.remoting.compat.Request} instance.
 */
public final class UnwrappingRequestHandler extends AbstractAutoCloseable<RequestHandler> implements RequestHandler {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.compat");

    private final RequestHandler next;

    /**
     * Basic constructor.
     *
     * @param executor the executor used to execute the close notification handlers
     * @param next
     */
    protected UnwrappingRequestHandler(final Executor executor, final RequestHandler next) {
        super(executor);
        this.next = next;
    }

    public RemoteRequestContext receiveRequest(final Object request, final ReplyHandler replyHandler) {
        if (request instanceof CompatabilityInvocationRequest) {
            final CompatabilityInvocationRequest invocationRequest = (CompatabilityInvocationRequest) request;
            final Map<Object,Object> map = invocationRequest.getRequestPayload();
            if (map == null) {
                return next.receiveRequest(invocationRequest.getArg(), new WrappingReplyHandler(replyHandler));
            }
            return null;
        } else {
            final RemoteRequestException nex = new RemoteRequestException("Expected a Remoting-2 InvocationRequest instance");
            try {
                replyHandler.handleException(nex);
            } catch (IOException e) {
                log.error(e, "Failed to forward an exception to the requesting party");
                log.error(nex, "The original exception follows");
            }
            return SpiUtils.getBlankRemoteRequestContext();
        }
    }
}
