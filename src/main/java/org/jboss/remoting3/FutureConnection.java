/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.xnio.FailedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class FutureConnection {

    private final EndpointImpl endpoint;
    private final URI uri;
    private final AtomicReference<FutureResult<Connection>> futureConnectionRef = new AtomicReference<FutureResult<Connection>>();
    private final boolean immediate;

    static final IoFuture.Notifier<Connection, FutureConnection> HANDLER = new IoFuture.HandlingNotifier<Connection, FutureConnection>() {
        public void handleCancelled(final FutureConnection attachment) {
            attachment.futureConnectionRef.set(null);
            if (attachment.immediate) {
                attachment.reconnectAfterDelay();
            }
        }

        public void handleFailed(final IOException exception, final FutureConnection attachment) {
            attachment.futureConnectionRef.set(null);
            if (attachment.immediate) {
                attachment.reconnectAfterDelay();
            }
        }

        public void handleDone(final Connection connection, final FutureConnection attachment) {
            connection.addCloseHandler(new CloseHandler<Connection>() {
                public void handleClose(final Connection closed, final IOException exception) {
                    if (attachment.immediate) {
                        attachment.connect(attachment.futureConnectionRef.get());
                    }
                }
            });
        }
    };

    FutureConnection(final EndpointImpl endpoint, final URI uri, final boolean immediate) {
        this.endpoint = endpoint;
        this.uri = uri;
        this.immediate = immediate;
    }

    void reconnectAfterDelay() {
        endpoint.getXnioWorker().getIoThread().executeAfter(FutureConnection.this::init, 30L, TimeUnit.SECONDS);
    }

    IoFuture<Connection> init() {
        return connect(null);
    }

    void splice(FutureResult<Connection> futureResult, IoFuture<Connection> realFuture) {
        // always add in this order
        futureResult.addCancelHandler(realFuture);
        realFuture.addNotifier(IoUtils.resultNotifier(), futureResult);
    }

    IoFuture<Connection> connect(FutureResult<Connection> orig) {
        AtomicReference<FutureResult<Connection>> futureConnectionRef = this.futureConnectionRef;
        FutureResult<Connection> oldVal;
        oldVal = futureConnectionRef.get();
        if (oldVal != orig) {
            return oldVal.getIoFuture();
        }
        final FutureResult<Connection> futureResult = new FutureResult<>();
        while (! futureConnectionRef.compareAndSet(oldVal, futureResult)) {
            oldVal = futureConnectionRef.get();
            if (oldVal != orig) {
                // discard our new one
                return oldVal.getIoFuture();
            }
        }
        IoFuture<Connection> realFuture;
        try {
            realFuture = endpoint.connect(uri);
        } catch (IOException e) {
            realFuture = new FailedIoFuture<>(e);
        }
        splice(futureResult, realFuture);
        final IoFuture<Connection> ioFuture = futureResult.getIoFuture();
        ioFuture.addNotifier(HANDLER, this);
        return ioFuture;
    }

    public IoFuture<Connection> get() {
        return init();
    }

    boolean isConnected() {
        final FutureResult<Connection> futureResult = futureConnectionRef.get();
        return futureResult != null && futureResult.getIoFuture().getStatus() == IoFuture.Status.DONE;
    }
}
