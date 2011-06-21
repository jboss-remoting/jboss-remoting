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

import java.security.Principal;
import java.util.Collection;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * A connection to a remote peer.
 */
public interface Connection extends HandleableCloseable<Connection>, Attachable {

    /**
     * Get the authenticated principals for this connection.
     *
     * @return the authenticated principals
     */
    Collection<Principal> getPrincipals();

    /**
     * Open a channel to a remote service on this connection.
     *
     * @param serviceType the service type
     * @param optionMap the option map
     * @return the future channel
     */
    IoFuture<Channel> openChannel(String serviceType, OptionMap optionMap);
}
