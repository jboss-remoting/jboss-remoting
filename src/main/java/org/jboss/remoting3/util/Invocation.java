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

package org.jboss.remoting3.util;

import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3._private.IntIndexer;

/**
 * A request-response invocation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class Invocation {
    static final IntIndexer<Invocation> INDEXER = Invocation::getIndex;

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
}
