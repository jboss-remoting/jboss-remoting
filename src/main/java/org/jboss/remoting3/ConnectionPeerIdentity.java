/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.jboss.remoting3;

import java.security.Principal;

import org.wildfly.security.auth.client.PeerIdentity;

/**
 * A connection peer identity.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConnectionPeerIdentity extends PeerIdentity {

    private final int index;
    private final Connection connection;

    ConnectionPeerIdentity(final Configuration configuration, final Principal peerPrincipal, final int index, final Connection connection) {
        super(configuration, peerPrincipal);
        this.index = index;
        this.connection = connection;
    }

    int getIndex() {
        return index;
    }

    /**
     * Get the connection of this identity.
     *
     * @return the connection of this identity (not {@code null})
     */
    public Connection getConnection() {
        return connection;
    }
}
