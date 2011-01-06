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

import org.jboss.remoting3.spi.RemoteRequestHandler;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.Result;

final class OutboundClient implements Cancellable {

    private State state = State.REPLY_WAIT;

    private Result<RemoteRequestHandler> result;

    private final int id;
    private final String serviceType;
    private final String groupName;
    private RemoteConnectionHandler remoteConnectionHandler;
    private RemoteRequestHandler requestHandler;

    OutboundClient(final RemoteConnectionHandler remoteConnectionHandler, final int id, final Result<RemoteRequestHandler> result, final String serviceType, final String groupName) {
        this.remoteConnectionHandler = remoteConnectionHandler;
        this.id = id;
        this.result = result;
        this.serviceType = serviceType;
        this.groupName = groupName;
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
            if (state != State.REPLY_WAIT) {
                return this;
            }
            state = State.CLOSED;
        }
        result.setCancelled();
        return this;
    }

    Result<RemoteRequestHandler> getResult() {
        return result;
    }

    String getServiceType() {
        return serviceType;
    }

    String getGroupName() {
        return groupName;
    }

    RemoteConnectionHandler getRemoteConnectionHandler() {
        return remoteConnectionHandler;
    }

    public int getId() {
        return id;
    }

    RemoteRequestHandler getRequestHandler() {
        return requestHandler;
    }

    void setResult(final RemoteRequestHandler requestHandler) {
        result.setResult(requestHandler);
        this.requestHandler = requestHandler;
    }

    enum State {
        REPLY_WAIT,
        ESTABLISHED,
        CLOSE_WAIT,
        CLOSED,
    }
}
