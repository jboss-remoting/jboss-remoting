/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.remoting3.util;

import static java.util.concurrent.locks.LockSupport.*;

import java.io.IOException;
import java.util.function.ToIntFunction;

import org.jboss.remoting3.MessageInputStream;

/**
 * A helper class for implementing pending responses for RPC-like systems.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class PendingResponse {
    public static final ToIntFunction<PendingResponse> PENDING_RESPONSE_INDEXER = PendingResponse::getReqId;

    private final int reqId;
    private final Thread waiter;
    private volatile Object obj;

    public PendingResponse(final int reqId, final Thread waiter) {
        this.reqId = reqId;
        this.waiter = waiter;
    }

    int getReqId() {
        return reqId;
    }

    public void setException(IOException e) {
        obj = e;
        unpark(waiter);
    }

    public void setResult(MessageInputStream is) {
        obj = is;
        unpark(waiter);
    }

    public MessageInputStream awaitInterruptibly() throws IOException, InterruptedException {
        assert waiter == Thread.currentThread();
        Object obj = this.obj;
        while (obj == null) {
            if (Thread.interrupted()) throw new InterruptedException();
            park(this);
        }
        if (obj instanceof IOException) {
            throw (IOException) obj;
        } else {
            return (MessageInputStream) obj;
        }
    }

    public MessageInputStream awaitUninterruptibly() throws IOException {
        assert waiter == Thread.currentThread();
        boolean intr = false;
        try {
            Object obj = this.obj;
            while (obj == null) {
                if (Thread.interrupted()) intr = true;
                park(this);
            }
            if (obj instanceof IOException) {
                throw (IOException) obj;
            } else {
                return (MessageInputStream) obj;
            }
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }
}
