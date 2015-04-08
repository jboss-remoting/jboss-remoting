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

package org.jboss.remoting3;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ConnectionKey {
    private final String hostName;
    private final String protocol;
    private final int port;
    private final int hashCode;

    ConnectionKey(final String hostName, final String protocol, final int port) {
        this.hostName = hostName;
        this.protocol = protocol;
        this.port = port;
        hashCode = (hostName.hashCode() * 17 + protocol.hashCode()) * 17 + port;
    }

    public String getHostName() {
        return hostName;
    }

    public String getProtocol() {
        return protocol;
    }

    public int getPort() {
        return port;
    }

    public int hashCode() {
        return hashCode;
    }

    public boolean equals(final Object obj) {
        return obj instanceof ConnectionKey && equals((ConnectionKey) obj);
    }

    boolean equals(ConnectionKey other) {
        return other != null && hashCode == other.hashCode && hostName.equals(other.hostName) && protocol.equals(other.protocol);
    }

    public String toString() {
        return String.format("Connection key for \"%s:%s:%d\"", protocol, hostName, Integer.valueOf(port));
    }
}
