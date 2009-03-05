/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.stream;

import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoFuture;
import java.nio.channels.Channel;
import java.io.Serializable;

/**
 * A stream handler for an individual object instance.  Instances of this class are used on both
 * the local and remote side.  Stream handlers are non-reentrant; in other words, it is an error
 * for a stream handler to have a stream type as one of its serializable fields or for a stream handler's
 * {@code writeObject} method (if any) to write a stream type.
 *
 * @param <T> the streamable object type
 * @param <C> the channel type that this handler uses
 */
public interface StreamHandler<T, C extends Channel> extends Serializable {

    /**
     * Get the local XNIO handler for this stream.  If this handler is cached on the object, it should be
     * done in a {@code transient} fashion to prevent the local handler from being sent to the remote side.
     *
     * @return the local XNIO handler
     */
    IoHandler<C> getLocalHandler();

    /**
     * Get the remote XNIO handler for this stream.  The remote handler should not be instantiated until the
     * {@code StreamHandler} instance is on the remote side to avoid copying the handler across the wire.
     *
     * @return the remote XNIO handler
     */
    IoHandler<C> getRemoteHandler();

    /**
     * Get the remote proxy instance for this stream.  The remote proxy should not be instantiated until the
     * {@code StreamHandler} instance is on the remote side to avoid copying the proxy instance across the wire.
     * This method will be called after {@link #getRemoteHandler()}.
     *
     * @param futureChannel the future channel
     * @return the remote proxy instance
     */
    T getRemoteProxy(final IoFuture<? extends C> futureChannel);
}
