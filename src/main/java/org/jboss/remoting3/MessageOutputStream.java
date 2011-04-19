/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
import java.io.OutputStream;
import org.xnio.Cancellable;

/**
 * An output stream for a message.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class MessageOutputStream extends OutputStream implements Cancellable {

    /**
     * Flush this message stream.  Any unwritten, buffered bytes are sent to the remote side.
     *
     * @throws IOException if an error occurs while flushing the stream
     */
    public abstract void flush() throws IOException;

    /**
     * Close this message stream.  If the stream is already closed or cancelled, this method has no effect.  After
     * this method is called, any further attempts to write to the stream will result in an exception.
     *
     * @throws IOException if a error occurs while closing the stream
     */
    public abstract void close() throws IOException;

    /**
     * Cancel this message stream.  If the stream is already closed or cancelled, this method has no effect.  After
     * this method is called, any further attempts to write to the stream will result in an exception.
     *
     * @return this stream
     */
    public abstract MessageOutputStream cancel();
}
