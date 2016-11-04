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

import java.util.Objects;

import org.wildfly.security.auth.client.AuthenticationConfiguration;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ConnectionKey {
    private final String protocol;
    private final String abstractType;
    private final String abstractTypeAuthority;
    private final AuthenticationConfiguration configuration;
    private final int hashCode;

    ConnectionKey(final String protocol, final String abstractType, final String abstractTypeAuthority, final AuthenticationConfiguration configuration) {
        this.protocol = protocol;
        this.abstractType = abstractType;
        this.abstractTypeAuthority = abstractTypeAuthority;
        this.configuration = configuration;
        hashCode = ((protocol.hashCode() * 17 + Objects.hashCode(abstractType)) * 17 + Objects.hashCode(abstractTypeAuthority)) * 17 + configuration.hashCode();
    }

    String getProtocol() {
        return protocol;
    }

    String getAbstractType() {
        return abstractType;
    }

    String getAbstractTypeAuthority() {
        return abstractTypeAuthority;
    }

    AuthenticationConfiguration getConfiguration() {
        return configuration;
    }

    public int hashCode() {
        return hashCode;
    }

    public boolean equals(final Object obj) {
        return obj instanceof ConnectionKey && equals((ConnectionKey) obj);
    }

    boolean equals(ConnectionKey other) {
        return other != null && hashCode == other.hashCode && protocol.equals(other.protocol) && Objects.equals(abstractType, other.abstractType) && Objects.equals(abstractTypeAuthority, other.abstractTypeAuthority) && configuration.equals(other.configuration);
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("Connection key for \"").append(protocol).append('"');
        if (abstractType != null) {
            b.append(" (abstract type \"").append(abstractType);
            if (abstractTypeAuthority != null) {
                b.append('.').append(abstractTypeAuthority);
            }
            b.append('"');
        }
        b.append(" config=").append(configuration);
        return b.toString();
    }
}
