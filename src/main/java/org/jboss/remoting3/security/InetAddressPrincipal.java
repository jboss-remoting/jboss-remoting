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

package org.jboss.remoting3.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;

/**
 * A principal representing an IP address.
 */
public final class InetAddressPrincipal implements Principal, Cloneable {
    private final InetAddress inetAddress;

    /**
     * Create a new instance.
     *
     * @param inetAddress the address
     */
    public InetAddressPrincipal(final InetAddress inetAddress) {
        if (inetAddress == null) {
            throw new IllegalArgumentException("inetAddress is null");
        }
        try {
            this.inetAddress = InetAddress.getByAddress(inetAddress.getHostAddress(), inetAddress.getAddress());
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Get the name of this principal; it will be the string representation of the IP address.
     *
     * @return the name of this principal
     */
    public String getName() {
        return inetAddress.getHostAddress();
    }

    /**
     * Get the IP address of this principal.
     *
     * @return the address
     */
    public InetAddress getInetAddress() {
        return inetAddress;
    }

    /**
     * Determine whether this instance is equal to another.
     *
     * @param other the other instance
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final Object other) {
        return other instanceof InetAddressPrincipal && equals((InetAddressPrincipal) other);
    }

    /**
     * Determine whether this instance is equal to another.
     *
     * @param other the other instance
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final InetAddressPrincipal other) {
        return other != null && inetAddress.equals(other.inetAddress);
    }

    /**
     * Get the hash code for this instance.  It will be equal to the hash code of the {@code InetAddress} object herein.
     *
     * @return the hash code
     */
    public int hashCode() {
        return inetAddress.hashCode();
    }

    /**
     * Get a human-readable representation of this principal.
     *
     * @return the string
     */
    public String toString() {
        return "InetAddressPrincipal <" + inetAddress.toString() + ">";
    }

    /**
     * Create a clone of this instance.
     *
     * @return the clone
     */
    public InetAddressPrincipal clone() {
        try {
            return (InetAddressPrincipal) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
