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

import org.jboss.remoting3.spi.RemoteReplyHandler;
import org.jboss.remoting3.RemoteExecutionException;
import java.io.IOException;

/**
 * A reply handler which unwraps a Remoting 2-style invocation response to a Remoting 3-style plain object.
 */
public final class UnwrappingReplyHandler implements RemoteReplyHandler {

    private final RemoteReplyHandler replyHandler;

    public UnwrappingReplyHandler(final RemoteReplyHandler replyHandler) {
        this.replyHandler = replyHandler;
    }

    public void handleReply(final Object reply) throws IOException {
        if (reply instanceof CompatabilityInvocationResponse) {
            final CompatabilityInvocationResponse response = (CompatabilityInvocationResponse) reply;
            if (response.isException()) {
                final Object result = response.getResult();
                if (result instanceof Throwable) {
                    replyHandler.handleException(new RemoteExecutionException("Remote execution failed", (Throwable) result));
                } else {
                    replyHandler.handleException(new RemoteExecutionException("Remote execution failed: " + result));
                }
            } else {
                replyHandler.handleReply(response.getPayload());
            }
        }
    }

    public void handleException(final IOException exception) throws IOException {
        replyHandler.handleException(exception);
    }

    public void handleCancellation() throws IOException {
        replyHandler.handleCancellation();
    }
}
