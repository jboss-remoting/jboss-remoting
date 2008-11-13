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

package org.jboss.remoting.core;

import java.util.concurrent.Executor;
import org.jboss.remoting.ClientContext;
import org.jboss.remoting.ServiceContext;

/**
 *
 */
public final class ClientContextImpl extends AbstractContextImpl<ClientContext> implements ClientContext {

    private final ServiceContextImpl serviceContext;

    ClientContextImpl(final Executor executor) {
        super(executor);
        serviceContext = null;
    }

    ClientContextImpl(final ServiceContextImpl serviceContext) {
        super(serviceContext.getExecutor());
        this.serviceContext = serviceContext;
    }

    public ServiceContext getServiceContext() {
        return serviceContext;
    }

    public String toString() {
        return "client context instance <" + Integer.toHexString(hashCode()) + ">";
    }
}
