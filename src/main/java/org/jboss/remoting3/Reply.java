/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

import java.io.IOException;
import java.io.InputStream;

/**
 * The result of a request.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Reply {

    /**
     * Get the reply stream.  If the request failed before any bytes were sent, this method will
     * throw an exception detailing the problem.  If the request failed after some bytes were sent, the bytes
     * that were successfully written will be readable from the given input stream, and after the last valid byte is
     * read, the next read will result in an thrown exception detailing the problem.
     * <p>
     * A stream from a successful reply will contain the entire reply, returning -1 from the {@code read()} methods when
     * the reply was fully read.  The stream may return -1 before the request is fully sent.
     * <p>
     * The returned stream should be closed regardless of the outcome of the operation (even if the request was
     * successfully cancelled or you have no interest in the reply content), in a {@code finally} block
     * or similar.
     *
     * @return the stream from which the reply may be read
     * @throws IOException if the request failed before sending any data back
     */
    InputStream getReplyStream() throws IOException;

    /**
     * Determine whether the request was successfully cancelled.  A cancelled request may still have some reply
     * content; the reply stream should be acquired, and closed regardless of whether cancellation was successful or
     * whether any data is read from it.
     * <p>
     * Note that the return value of this method may change if a cancellation request is sent and acknowledged while the
     * reply is being received.
     *
     * @return {@code true} if the request has been cancelled, {@code false} otherwise
     */
    boolean wasCancelled();
}
