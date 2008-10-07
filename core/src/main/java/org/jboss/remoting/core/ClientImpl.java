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

package org.jboss.remoting.core;

import org.jboss.remoting.Client;
import org.jboss.remoting.RemotingException;
import org.jboss.remoting.core.util.QueueExecutor;
import org.jboss.remoting.spi.remote.RequestHandler;
import org.jboss.remoting.spi.remote.ReplyHandler;
import org.jboss.remoting.spi.remote.RemoteRequestContext;
import org.jboss.remoting.spi.remote.Handle;
import org.jboss.xnio.IoFuture;
import org.jboss.marshalling.Externalizer;
import org.jboss.marshalling.Creator;
import java.util.concurrent.Executor;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectInput;

/**
 *
 */
public final class ClientImpl<I, O> extends AbstractContextImpl<Client<I, O>> implements Client<I, O> {

    private final Handle<RequestHandler> handle;

    ClientImpl(final Handle<RequestHandler> handle, final Executor executor) {
        super(executor);
        this.handle = handle;
    }

    protected void closeAction() throws IOException {
        handle.close();
    }

    public O invoke(final I request) throws IOException {
        if (! isOpen()) {
            throw new RemotingException("Client is not open");
        }
        final QueueExecutor executor = new QueueExecutor();
        final FutureReplyImpl<O> futureReply = new FutureReplyImpl<O>(executor);
        final ReplyHandler replyHandler = futureReply.getReplyHandler();
        final RemoteRequestContext requestContext = handle.getResource().receiveRequest(request, replyHandler);
        futureReply.setRemoteRequestContext(requestContext);
        futureReply.addNotifier(new IoFuture.Notifier<O>() {
            public void notify(final IoFuture<O> future) {
                executor.shutdown();
            }
        });
        executor.runQueue();
        return futureReply.get();
    }

    public IoFuture<O> send(final I request) throws IOException {
        if (! isOpen()) {
            throw new RemotingException("Client is not open");
        }
        final FutureReplyImpl<O> futureReply = new FutureReplyImpl<O>(executor);
        final ReplyHandler replyHandler = futureReply.getReplyHandler();
        final RemoteRequestContext requestContext = handle.getResource().receiveRequest(request, replyHandler);
        futureReply.setRemoteRequestContext(requestContext);
        return futureReply;
    }

    public void sendOneWay(final I request) throws RemotingException {
        if (! isOpen()) {
            throw new RemotingException("Client is not open");
        }
        handle.getResource().receiveRequest(request);
    }

    public String toString() {
        return "client instance <" + Integer.toString(hashCode()) + ">";
    }

    Handle<RequestHandler> getRequestHandlerHandle() {
        return handle;
    }
}
