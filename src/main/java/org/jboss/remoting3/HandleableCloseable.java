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
