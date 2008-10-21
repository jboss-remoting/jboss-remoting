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

package org.jboss.remoting.protocol.multiplex;

/**
 *
 */
public enum ConfigValue {

    /**
     * The protocol version to use.  Value type is {@code int}.
     */
    PROTOCOL_VERSION(0),
    /**
     * The name of the marshaller to use.  Value type is {@code String}.
     */
    MARSHALLER_NAME(1),
    ;
    private final int id;

    private ConfigValue(final int id) {
        this.id = id;
    }

    /**
     * Get the integer ID for this config value.
     *
     * @return the integer ID
     */
    public int getId() {
        return id;
    }

    /**
     * Get the config value for an integer ID.
     *
     * @param id the integer ID
     * @return the config value instance
     */
    public static ConfigValue getConfigValue(final int id) {
        switch (id) {
            case 0: return PROTOCOL_VERSION;
            case 1: return MARSHALLER_NAME;
            default: throw new IllegalArgumentException("Invalid config value ID");
        }
    }
}
