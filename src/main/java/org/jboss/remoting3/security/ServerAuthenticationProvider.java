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

package org.jboss.remoting3.security;

/**
 *
 */
public interface ServerAuthenticationProvider {

    /**
     * Get a callback handler for the given mechanism name.
     * 
     * This method is called each time a mechanism is selected for the connection and the resulting 
     * AuthorizingCallbackHandler will be cached and used multiple times for this connection, AuthorizingCallbackHandler
     * should either be thread safe or the ServerAuthenticationProvider should provide a new instance each time called.
     *
     * @param mechanismName the SASL mechanism to get a callback handler for
     * @return the callback handler or {@code null} if the mechanism is not supported
     */
    AuthorizingCallbackHandler getCallbackHandler(String mechanismName);
    
}
