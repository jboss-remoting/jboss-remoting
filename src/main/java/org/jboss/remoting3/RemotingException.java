/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3;

import java.io.IOException;

/**
 * A general Remoting exception.  Used as a base class in order to provide constructors which accept any combination
 * of {@code cause} and {@code message}.
 */
public abstract class RemotingException extends IOException {

    private static final long serialVersionUID = 1540716301579397423L;

    /**
     * Constructs a <tt>RemotingException</tt> with no detail message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    protected RemotingException() {
    }

    /**
     * Constructs a <tt>RemotingException</tt> with the specified detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    protected RemotingException(String msg) {
        super(msg);
    }

    /**
     * Constructs a <tt>RemotingException</tt> with the specified cause. The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of <tt>cause</tt>).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    protected RemotingException(Throwable cause) {
        initCause(cause);
    }

    /**
     * Constructs a <tt>RemotingException</tt> with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    protected RemotingException(String msg, Throwable cause) {
        super(msg);
        initCause(cause);
    }
}
