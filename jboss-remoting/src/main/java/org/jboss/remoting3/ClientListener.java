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

package org.jboss.remoting3;

import java.util.EventListener;
import org.jboss.xnio.OptionMap;

/**
 * A client listener associated with a service.  When a client is opened for this service, a new request listener
 * is created and returned.
 *
 * @param <I> the request type
 * @param <O> the reply type
 *
 * @apiviz.landmark
 */
public interface ClientListener<I, O> extends EventListener {

    /**
     * Handle a client open by returning a new request listener.  The supplied client context may be used to register
     * a notifier for when the client is closed.
     * <p/>
     * If {@code null} is returned, the client is closed with an error.
     *
     * @param clientContext the client context
     * @param optionMap the service open options
     * @return the request listener
     */
    RequestListener<I, O> handleClientOpen(ClientContext clientContext, OptionMap optionMap);
}
