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

package org.jboss.remoting3.remote;

import java.util.concurrent.Semaphore;
import org.jboss.marshalling.NioByteInput;
import org.jboss.xnio.Cancellable;

final class InboundRequest {

    private final Semaphore flowSemaphore = new Semaphore(5);

    private Cancellable cancellable;
    private OutboundReplyHandler replyHandler;
    private final NioByteInput byteInput;
    private final RemoteConnectionHandler remoteConnectionHandler;
    private final int rid;
    private State state = State.RECEIVING;

    InboundRequest(final RemoteConnectionHandler remoteConnectionHandler, final int rid) {
        this.remoteConnectionHandler = remoteConnectionHandler;
        byteInput = new NioByteInput(new InboundRequestInputHandler(this, rid));
        this.rid = rid;
    }

    void ack() {
        flowSemaphore.release();
    }

    NioByteInput getByteInput() {
        return byteInput;
    }

    OutboundReplyHandler getReplyHandler() {
        return replyHandler;
    }

    void acquire() throws InterruptedException {
        flowSemaphore.acquire();
    }

    void setReplyHandler(final OutboundReplyHandler replyHandler) {
        this.replyHandler = replyHandler;
    }

    void setCancellable(final Cancellable cancellable) {
        this.cancellable = cancellable;
    }

    public Cancellable getCancellable() {
        return cancellable;
    }

    enum State {
        RECEIVING,
        RUNNING,
        SENDING,
        SENDING_EXCEPTION,
    }

    RemoteConnectionHandler getRemoteConnectionHandler() {
        return remoteConnectionHandler;
    }

    public String toString() {
        return "Inbound request ID " + rid;
    }
}
