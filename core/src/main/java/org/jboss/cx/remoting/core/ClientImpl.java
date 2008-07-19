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

package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RequestCompletionHandler;
import org.jboss.cx.remoting.core.util.QueueExecutor;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import org.jboss.cx.remoting.spi.remote.ReplyHandler;
import org.jboss.cx.remoting.spi.remote.RemoteRequestContext;
import java.util.concurrent.Executor;

/**
 *
 */
public final class ClientImpl<I, O> extends AbstractContextImpl<Client<I, O>> implements Client<I, O> {

    private final RemoteClientEndpoint remoteClientEndpoint;

    ClientImpl(final RemoteClientEndpoint remoteClientEndpoint, final Executor executor) {
        super(executor);
        this.remoteClientEndpoint = remoteClientEndpoint;
    }

    public O invoke(final I request) throws RemotingException, RemoteExecutionException {
        if (! isOpen()) {
            throw new RemotingException("Client is not open");
        }
        final QueueExecutor executor = new QueueExecutor();
        final FutureReplyImpl<O> futureReply = new FutureReplyImpl<O>(executor);
        final ReplyHandler replyHandler = futureReply.getReplyHandler();
        final RemoteRequestContext requestContext = remoteClientEndpoint.receiveRequest(request, replyHandler);
        futureReply.setRemoteRequestContext(requestContext);
        futureReply.addCompletionHandler(new RequestCompletionHandler<O>() {
            public void notifyComplete(final FutureReply<O> reply) {
                executor.shutdown();
            }
        });
        executor.runQueue();
        return futureReply.get();
    }

    public FutureReply<O> send(final I request) throws RemotingException {
        if (! isOpen()) {
            throw new RemotingException("Client is not open");
        }
        final FutureReplyImpl<O> futureReply = new FutureReplyImpl<O>(executor);
        final ReplyHandler replyHandler = futureReply.getReplyHandler();
        final RemoteRequestContext requestContext = remoteClientEndpoint.receiveRequest(request, replyHandler);
        futureReply.setRemoteRequestContext(requestContext);
        return futureReply;
    }

    public void sendOneWay(final I request) throws RemotingException {
        if (! isOpen()) {
            throw new RemotingException("Client is not open");
        }
        remoteClientEndpoint.receiveRequest(request);
    }

    public String toString() {
        return "client instance <" + Integer.toString(hashCode()) + ">";
    }
}
