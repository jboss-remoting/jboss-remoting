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

import org.jboss.remoting.spi.ReplyHandler;
import java.io.IOException;

/**
 * A request handler which wraps a Remoting 3-style reply with a Remoting 2-style invocation response.
 */
public final class WrappingReplyHandler implements ReplyHandler {

    private final ReplyHandler replyHandler;

    public WrappingReplyHandler(final ReplyHandler replyHandler) {
        this.replyHandler = replyHandler;
    }

    public void handleReply(final Object reply) throws IOException {
        replyHandler.handleReply(new CompatabilityInvocationResponse(null, reply,  false, null));
    }

    public void handleException(final IOException exception) throws IOException {
        replyHandler.handleReply(new CompatabilityInvocationResponse(null, exception, true, null));
    }

    public void handleCancellation() throws IOException {
        replyHandler.handleCancellation();
    }
}
