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

import java.util.concurrent.Executor;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.xnio.log.Logger;

/**
 *
 */
final class ClientContextImpl extends AbstractHandleableCloseable<ClientContext> implements ClientContext {

    @SuppressWarnings({ "UnusedDeclaration" })
    private static final Logger log = Logger.getLogger("org.jboss.remoting.client-context");

    private final Connection connection;

    ClientContextImpl(final Executor executor, final Connection connection) {
        super(executor);
        this.connection = connection;
    }

    protected Executor getExecutor() {
        return super.getExecutor();
    }

    public Connection getConnection() {
        return connection;
    }

    public String toString() {
        return "client context instance <" + Integer.toHexString(hashCode()) + ">";
    }
}
