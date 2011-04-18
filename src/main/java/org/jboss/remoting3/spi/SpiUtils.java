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
import org.jboss.remoting3.CloseHandler;
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
     * @param <T> the type of the closable resource
     * @param handler the close handler
     * @param closed the object that was closed
     */
    public static <T> void safeHandleClose(final CloseHandler<? super T> handler, final T closed) {
        try {
            if (handler != null && closed != null) handler.handleClose(closed);
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
            public void handleClose(final Object closed) {
                IoUtils.safeClose(c);
            }
        };
    }
}
