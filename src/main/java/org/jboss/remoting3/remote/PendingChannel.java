/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3.remote;

import java.util.function.ToIntFunction;

import org.jboss.remoting3.Channel;
import org.xnio.Result;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class PendingChannel {
    private final int id;
    private final int outboundWindowSize;
    private final int inboundWindowSize;
    private final int outboundMessageCount;
    private final int inboundMessageCount;
    private final long outboundMessageSize;
    private final long inboundMessageSize;
    private final int messageAckTimeout;
    private final Result<Channel> result;

    PendingChannel(final int id, final int outboundWindowSize, final int inboundWindowSize, final int outboundMessageCount, final int inboundMessageCount, final long outboundMessageSize, final long inboundMessageSize, final int messageAckTimeout, final Result<Channel> result) {
        this.id = id;
        this.outboundWindowSize = outboundWindowSize;
        this.inboundWindowSize = inboundWindowSize;
        this.outboundMessageCount = outboundMessageCount;
        this.inboundMessageCount = inboundMessageCount;
        this.outboundMessageSize = outboundMessageSize;
        this.inboundMessageSize = inboundMessageSize;
        this.messageAckTimeout = messageAckTimeout;
        this.result = result;
    }

    int getId() {
        return id;
    }

    int getOutboundWindowSize() {
        return outboundWindowSize;
    }

    int getInboundWindowSize() {
        return inboundWindowSize;
    }

    int getOutboundMessageCount() {
        return outboundMessageCount;
    }

    int getInboundMessageCount() {
        return inboundMessageCount;
    }

    long getOutboundMessageSize() {
        return outboundMessageSize;
    }

    long getInboundMessageSize() {
        return inboundMessageSize;
    }

    int getMessageAckTimeout() {
        return messageAckTimeout;
    }

    Result<Channel> getResult() {
        return result;
    }

    static final ToIntFunction<PendingChannel> INDEXER = PendingChannel::getId;
}
