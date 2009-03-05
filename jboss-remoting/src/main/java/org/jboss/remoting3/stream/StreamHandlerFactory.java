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

import java.nio.channels.Channel;
import java.io.IOException;

/**
 * A stream handler factory.  Produces stream handler instances for the given object, which in turn uses a specified
 * channel type to stream the data.
 *
 * @param <T> the streamable object type
 * @param <C> the channel type that this handler uses
 */
public interface StreamHandlerFactory<T, C extends Channel> {

    /**
     * Create a stream handler instance for a local object.
     *
     * @param localInstance the local instance
     * @param streamContext the stream context
     * @return the stream handler for this type
     * @throws IOException if an error occurs
     */
    StreamHandler<T, C> createStreamHandler(T localInstance, StreamContext streamContext) throws IOException;
}
