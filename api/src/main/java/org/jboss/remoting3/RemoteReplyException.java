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

package org.jboss.remoting3;

/**
 * An exception indicating that a the remote side tried and failed to send a reply message.  The remote side would be
 * aware of this type of failure, so the outcome is determinate; thus it extends {@code RemoteExecutionException}.
 */
public class RemoteReplyException extends RemoteExecutionException {

    private static final long serialVersionUID = -8572480018652753441L;

    /**
     * Constructs a <tt>RemoteReplyException</tt> with no detail message. The cause is not initialized, and may subsequently
     * be initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public RemoteReplyException() {
    }

    /**
     * Constructs a <tt>RemoteReplyException</tt> with the specified detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public RemoteReplyException(String msg) {
        super(msg);
    }

    /**
     * Constructs a <tt>RemoteReplyException</tt> with the specified cause. The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of <tt>cause</tt>).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public RemoteReplyException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a <tt>RemoteReplyException</tt> with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public RemoteReplyException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
