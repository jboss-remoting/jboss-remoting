/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, JBoss Inc., and individual contributors as indicated
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

import java.security.Principal;
import java.util.Collection;
import java.util.Iterator;

/**
 * A simple UserInfo implementation that takes the the supplied Prinicpals to extract the user name for the user.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SimpleUserInfo implements UserInfo {
    final String userName;

    public SimpleUserInfo(Collection<Principal> remotingPrincipals) {
        String userName = null;
        Iterator<Principal> principals = remotingPrincipals.iterator();
        while (userName == null && principals.hasNext()) {
            Principal next = principals.next();
            if (next instanceof UserPrincipal) {
                userName = ((UserPrincipal) next).getName();
            }
        }
        this.userName = userName;

    }

    public String getUserName() {
        return userName;
    }
}