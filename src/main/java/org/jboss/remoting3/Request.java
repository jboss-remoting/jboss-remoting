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

import java.io.OutputStream;
import org.xnio.Cancellable;

/**
 * A Remoting request.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Request extends Cancellable {

    /**
     * Get the output stream to which the request will be written.  Calling this
     * method multiple times will return the same stream.  Closing the stream marks
     * the end of the message; however the reply may be received in full or in part before
     * the request is finished being written.
     *
     * @return the request output stream
     */
    OutputStream getOutputStream();

    /**
     * Attempt to cancel the current request.  The cancellation request may be sent to the remote
     * side as an out-of-band message; or, it may not be supported at all.  The request stream should still
     * be closed before being discarded, even if cancellation was requested.
     *
     * @return this request instance
     */
    Request cancel();
}
