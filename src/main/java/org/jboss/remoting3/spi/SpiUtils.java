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
        final StackTraceElement[] fst = Arrays.copyOf(est, est.length + userStackTrace.length - trimCount + 1);
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
