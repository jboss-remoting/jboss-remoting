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

package org.jboss.remoting3;

import java.io.Closeable;
import java.io.IOException;

/**
 * A Remoting resource that can be closed.
 *
 * @param <T> the type that is passed to the close handler
 *
 * @apiviz.exclude
 */
public interface HandleableCloseable<T> extends Closeable {

    /**
     * Close this resource.  Call any registered close handlers.  Calling this method more than once will not have
     * any additional effect.
     *
     * @throws IOException if the close failed
     */
    void close() throws IOException;

    /**
     * Wait for a resource close to complete.
     *
     * @throws InterruptedException if the operation is interrupted
     */
    void awaitClosed() throws InterruptedException;

    /**
     * Wait for a resource close to complete.
     */
    void awaitClosedUninterruptibly();

    /**
     * Asynchronously close this resource.  Returns immediately.
     */
    void closeAsync();

    /**
     * Add a handler that will be called upon close.  If the resource is already closed, the handler will be called
     * immediately.
     *
     * @param handler the close handler
     * @return a key which may be used to later remove this handler
     */
    Key addCloseHandler(CloseHandler<? super T> handler);

    /**
     * Read the status of this resource.  This is just a snapshot in time; there is no guarantee that the resource
     * will remain open for any amount of time, even if this method returns {@code true}.
     *
     * @return {@code true} if the resource is still open
     */
    boolean isOpen();

    /**
     * A key which may be used to remove this handler.
     *
     * @apiviz.exclude
     */
    interface Key {

        /**
         * Remove the registered handler.  Calling this method more than once has no additional effect.
         */
        void remove();
    }
}
