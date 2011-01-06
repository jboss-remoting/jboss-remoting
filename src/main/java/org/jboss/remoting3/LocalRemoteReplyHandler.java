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

import java.io.IOException;
import org.jboss.marshalling.cloner.ObjectCloner;
import org.jboss.remoting3.spi.LocalReplyHandler;
import org.jboss.remoting3.spi.RemoteReplyHandler;

class LocalRemoteReplyHandler implements RemoteReplyHandler {

    private final LocalReplyHandler replyHandler;
    private final ObjectCloner replyCloner;

    public LocalRemoteReplyHandler(final LocalReplyHandler replyHandler, final ObjectCloner replyCloner) {
        this.replyHandler = replyHandler;
        this.replyCloner = replyCloner;
    }

    public void handleReply(final Object reply) throws IOException {
        try {
            replyHandler.handleReply(replyCloner.clone(reply));
        } catch (ClassNotFoundException e) {
            final ReplyException re = new ReplyException("Cannot clone reply", e);
            replyHandler.handleException(re);
            throw re;
        }
    }

    public void handleException(final IOException exception) throws IOException {
        try {
            replyHandler.handleException((IOException) replyCloner.clone(exception));
        } catch (ClassNotFoundException e) {
            final ReplyException re = new ReplyException("Cannot clone reply", e);
            replyHandler.handleException(re);
            throw re;
        }
    }

    public void handleCancellation() throws IOException {
        replyHandler.handleCancellation();
    }

    public ClassLoader getClassLoader() {
        return replyHandler.getClassLoader();
    }
}
