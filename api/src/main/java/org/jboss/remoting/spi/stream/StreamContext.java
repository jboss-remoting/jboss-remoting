/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting.spi.stream;

import java.util.concurrent.Executor;
import java.io.IOException;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Unmarshaller;

/**
 * A context for stream serialization.
 */
public interface StreamContext {

    /**
     * Get an executor which may be used by a stream serializer for various asynchronous tasks.
     *
     * @return an executor
     */
    Executor getExecutor();

    /**
     * Create a marshaller which is configured compatibly with the channel.
     *
     * @return a marshaller
     */
    Marshaller createMarshaller() throws IOException;

    /**
     * Create an unmarshaller which is configured compatibly with the channel.
     *
     * @return an unmarshaller
     */
    Unmarshaller createUnmarshaller() throws IOException;

    // todo - getter & setter for child ObjectTable, ClassTable, ExternalizerFactory, etc. for marshaller and unmarshaller
}
