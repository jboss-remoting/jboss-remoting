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
import org.jboss.remoting3.spi.LocalReplyHandler;
import org.jboss.xnio.Cancellable;

final class OutboundRequest implements Cancellable {
    private final int cid;
    private final LocalReplyHandler inboundReplyHandler;
    private final Semaphore flowSemaphore = new Semaphore(5); // todo receive window size

    private State state = State.SENDING;
    private NioByteInput byteInput;
    private RemoteConnectionHandler remoteConnectionHandler;

    OutboundRequest(final RemoteConnectionHandler remoteConnectionHandler, final LocalReplyHandler inboundReplyHandler, final int cid) {
        this.remoteConnectionHandler = remoteConnectionHandler;
        this.inboundReplyHandler = inboundReplyHandler;
        this.cid = cid;
    }

    State getState() {
        synchronized (this) {
            return state;
        }
    }

    void setState(final State state) {
        synchronized (this) {
            this.state = state;
        }
    }

    public Cancellable cancel() {
        synchronized (this) {
            switch (state) {
                case SENDING:
                    // todo: send cancel request, kill in-progress stream
                    break;
                case REPLY_WAIT:
                    // todo: send cancel request
                    break;
                case CANCEL_WAIT:
                case RECEIVING:
                case CLOSED:
                    // do nothing
                    break;
            }
        }
        return this;
    }

    void setByteInput(final NioByteInput byteInput) {
        this.byteInput = byteInput;
    }

    NioByteInput getByteInput() {
        return byteInput;
    }

    void ack() {
        flowSemaphore.release();
    }

    RemoteConnectionHandler getRemoteConnectionHandler() {
        return remoteConnectionHandler;
    }

    public int getClientId() {
        return cid;
    }

    public void acquire() throws InterruptedException {
        flowSemaphore.acquire();
    }

    enum State {
        SENDING,
        REPLY_WAIT,
        CANCEL_WAIT,
        RECEIVING,
        CLOSED,
    }

    LocalReplyHandler getInboundReplyHandler() {
        return inboundReplyHandler;
    }

    public String toString() {
        return "Outbound Request for client ID " + cid;
    }
}
