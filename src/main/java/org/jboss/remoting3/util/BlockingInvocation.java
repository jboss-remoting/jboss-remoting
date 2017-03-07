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

import static org.xnio.IoUtils.safeClose;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;

import org.jboss.remoting3.ChannelClosedException;
import org.jboss.remoting3.MessageInputStream;

/**
 * A blocking invocation.  This class may be used as-is or subclassed for additional functionality.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class BlockingInvocation extends Invocation {

    private final ArrayDeque<Response> responses = new ArrayDeque<>(2);
    private boolean cancelled;

    /**
     * Construct a new instance.
     *
     * @param index the invocation index
     */
    public BlockingInvocation(final int index) {
        super(index);
    }

    /**
     * Get the next queued response, waiting if necessary.  The returned response <em>must</em> be closed.
     *
     * @return the queued response
     * @throws InterruptedException if the thread was interrupted while waiting
     */
    public Response getResponse() throws InterruptedException {
        final ArrayDeque<Response> responses = this.responses;
        synchronized (responses) {
            if (cancelled) {
                throw new IllegalStateException("Waiting on cancelled response");
            }
            while (responses.isEmpty()) {
                responses.wait();
            }
            return responses.pollFirst();
        }
    }

    public void handleResponse(final int parameter, final MessageInputStream inputStream) {
        final ArrayDeque<Response> responses = this.responses;
        synchronized (responses) {
            if (cancelled) {
                safeClose(inputStream);
                return;
            }
            responses.add(new Response(inputStream, parameter, null));
            responses.notify();
        }
    }

    public void handleClosed() {
        final ArrayDeque<Response> responses = this.responses;
        synchronized (responses) {
            responses.add(new Response(null, 0, null));
            responses.notify();
        }
    }

    public void handleException(final IOException exception) {
        final ArrayDeque<Response> responses = this.responses;
        synchronized (responses) {
            responses.add(new Response(null, 0, exception));
            responses.notify();
        }
    }

    /**
     * Cancel the invocation, causing all future responses to be closed without being read.  This method should only
     * be called from the waiting thread (for example, in response to thread interruption).
     */
    public void cancel() {
        final ArrayDeque<Response> responses = this.responses;
        synchronized (responses) {
            while (! responses.isEmpty()) {
                safeClose(responses.poll());
            }
            cancelled = true;
            responses.notifyAll();
        }
    }

    /**
     * An invocation response for a blocking invocation.
     */
    public final class Response implements Closeable {

        private final MessageInputStream inputStream;
        private final int parameter;
        private final IOException thrown;

        Response(final MessageInputStream inputStream, final int parameter, final IOException thrown) {
            this.inputStream = inputStream;
            this.parameter = parameter;
            this.thrown = thrown;
        }

        /**
         * Get the message input stream.
         *
         * @return the queued input stream
         * @throws IOException if the channel was closed, or if another I/O error occurs
         */
        public MessageInputStream getInputStream() throws IOException {
            if (thrown != null) throw thrown;
            final MessageInputStream inputStream = this.inputStream;
            if (inputStream == null) {
                throw new ChannelClosedException("Channel was closed");
            }
            return inputStream;
        }

        /**
         * Get the passed-in parameter.
         *
         * @return the parameter (or 0 if the channel was closed)
         */
        public int getParameter() {
            return parameter;
        }

        /**
         * Close this response; closes the message stream (if any).
         *
         * @throws IOException if closing the message stream failed for some reason
         */
        public void close() throws IOException {
            final MessageInputStream inputStream = this.inputStream;
            if (inputStream != null) inputStream.close();
        }
    }
}
