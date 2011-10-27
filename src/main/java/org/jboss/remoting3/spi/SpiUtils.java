/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.spi;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.HandleableCloseable;
import org.jboss.remoting3.OpenListener;
import org.xnio.IoUtils;
import org.jboss.logging.Logger;

/**
 * Utility methods for Remoting service providers.
 */
public final class SpiUtils {

    private SpiUtils() {}

    private static final Logger heLog = Logger.getLogger("org.jboss.remoting.handler-errors");

    /**
     * Safely handle a close notification.
     *
     * @param handler the close handler
     * @param closed the object that was closed
     * @param exception the close exception, or {@code null} if the close succeeded
     * @param <T> the type of the closed resource
     */
    public static <T> void safeHandleClose(final CloseHandler<? super T> handler, final T closed, final IOException exception) {
        try {
            if (handler != null && closed != null) handler.handleClose(closed, exception);
        } catch (Throwable t) {
            heLog.error("Close handler threw an exception", t);
        }
    }

    /**
     * A close handler which closes another resource.
     *
     * @param c the resource to close
     * @return the close handler
     */
    public static CloseHandler<Object> closingCloseHandler(final Closeable c) {
        return new CloseHandler<Object>() {
            public void handleClose(final Object closed, final IOException exception) {
                IoUtils.safeClose(c);
            }
        };
    }

    /**
     * A close handler which closes another resource asynchronously.
     *
     * @param c the resource to close
     * @return the close handler
     */
    public static CloseHandler<Object> asyncClosingCloseHandler(final HandleableCloseable<?> c) {
        return new CloseHandler<Object>() {
            public void handleClose(final Object closed, final IOException exception) {
                c.closeAsync();
            }
        };
    }

    /**
     * Glue two stack traces together.
     *
     * @param exception the exception which occurred in another thread
     * @param userStackTrace the stack trace of the current thread from {@link Thread#getStackTrace()}
     * @param trimCount the number of frames to trim
     * @param msg the message to use
     */
    public static void glueStackTraces(final Throwable exception, final StackTraceElement[] userStackTrace, final int trimCount, final String msg) {
        final StackTraceElement[] est = exception.getStackTrace();
        final StackTraceElement[] fst = Arrays.copyOf(est, est.length + userStackTrace.length);
        fst[est.length] = new StackTraceElement("..." + msg + "..", "", null, -1);
        System.arraycopy(userStackTrace, trimCount, fst, est.length + 1, userStackTrace.length - trimCount);
        exception.setStackTrace(fst);
    }

    /**
     * Get an executor task for opening a service.
     *
     * @param newChannel the new service channel
     * @param listener the service open listener
     * @return the runnable task
     */
    public static Runnable getServiceOpenTask(final Channel newChannel, final OpenListener listener) {
        return new ServiceOpenTask(listener, newChannel);
    }

    static class ServiceOpenTask implements Runnable {

        private final OpenListener listener;
        private final Channel newChannel;

        public ServiceOpenTask(final OpenListener listener, final Channel newChannel) {
            this.listener = listener;
            this.newChannel = newChannel;
        }

        public void run() {
            listener.channelOpened(newChannel);
        }
    }
}
