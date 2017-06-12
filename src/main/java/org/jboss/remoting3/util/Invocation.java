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

package org.jboss.remoting3.util;

import java.io.IOException;

import org.jboss.remoting3.MessageInputStream;

/**
 * A request-response invocation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class Invocation {

    private final int index;

    /**
     * Construct a new instance.
     *
     * @param index the invocation index
     */
    protected Invocation(final int index) {
        this.index = index;
    }

    /**
     * Get the invocation index.
     *
     * @return the invocation index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Handle a response on this invocation.  The response may be final or it may be an update.  Long tasks should
     * be executed in a worker thread.  This method must guarantee that {@code inputStream} is closed.
     *
     * @param parameter the numeric parameter passed in to the tracker
     * @param inputStream the body of the message
     */
    public abstract void handleResponse(int parameter, MessageInputStream inputStream);

    /**
     * Handle closure of the channel.
     */
    public abstract void handleClosed();

    /**
     * Handle a failure that occurred on the channel while the invocation was outstanding.
     *
     * @param exception the exception that was thrown
     */
    public abstract void handleException(final IOException exception);
}
