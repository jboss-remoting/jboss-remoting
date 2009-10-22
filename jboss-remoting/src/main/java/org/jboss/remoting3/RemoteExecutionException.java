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

package org.jboss.remoting3;

/**
 * Exception thrown when execution of a remote operation fails for some reason.
 */
public class RemoteExecutionException extends RemotingException {

    private static final long serialVersionUID = 3580395686019440048L;

    /**
     * Constructs a <tt>RemoteExecutionException</tt> with no detail message.
     * The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public RemoteExecutionException() {
    }

    /**
     * Constructs a <tt>RemoteExecutionException</tt> with the specified detail
     * message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public RemoteExecutionException(String msg) {
        super(msg);
    }

    /**
     * Constructs a <tt>RemoteExecutionException</tt> with the specified cause.
     * The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of
     * <tt>cause</tt>).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public RemoteExecutionException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a <tt>RemoteExecutionException</tt> with the specified detail
     * message and cause.
     *
     * @param  msg the detail message
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public RemoteExecutionException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Convenience method to rethrow the cause of a {@code RemoteExecutionException} as a specific type, in order
     * to simplify application exception handling.
     * <p/>
     * A typical usage might look like this:
     * <pre>
     *   try {
     *     client.invoke(request);
     *   } catch (RemoteExecutionException ree) {
     *     ree.rethrow(IOException.class);
     *     ree.rethrow(RuntimeException.class);
     *     throw ree.unexpected();
     *   }
     * </pre>
     * <p/>
     * Note that if the nested exception is an {@link InterruptedException}, the type that will actually be thrown
     * will be {@link RemoteInterruptedException}.
     *
     * @param type the class of the exception
     * @param <T> the exception type
     * @throws T the exception, if it matches the given type
     */
    public <T extends Throwable> void rethrow(Class<T> type) throws T {
        final Throwable cause = getCause();
        if (cause == null) {
            return;
        }
        if (type.isAssignableFrom(cause.getClass()) || type == RemoteInterruptedException.class) {
            if (cause instanceof InterruptedException) {
                final RemoteInterruptedException rie = new RemoteInterruptedException(cause.getMessage(), cause.getCause());
                rie.setStackTrace(cause.getStackTrace());
                throw rie;
            }
            throw type.cast(cause);
        }
        return;
    }

    /**
     * Convenience method to get an unexpected exception type wrapped within a runtime exception.
     */
    public IllegalStateException unexpected() {
        Throwable cause = getCause();
        if (cause instanceof InterruptedException) {
            cause = new RemoteInterruptedException(cause.getMessage(), cause.getCause());
        }
        throw new IllegalStateException("Unexpected remote exception occurred", cause);
    }
}
